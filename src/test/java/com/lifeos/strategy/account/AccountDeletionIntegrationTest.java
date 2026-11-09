package com.lifeos.strategy.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifeos.base.id.UuidV7;
import com.lifeos.strategy.account.consumer.UserDeletionConsumer;
import com.lifeos.strategy.proposal.consumer.CoachEventConsumer;
import com.lifeos.strategy.support.EventSchemas;
import com.lifeos.strategy.support.IntegrationTest;
import com.lifeos.strategy.support.RecordingIdentityAccounts;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** KVKK deletion: every row of the user goes, nothing of anyone else, the audit trail stays. */
class AccountDeletionIntegrationTest extends IntegrationTest {

    private static final String W40 = "2026-W40";

    @Autowired
    private CoachEventConsumer coachEventConsumer;

    @Autowired
    private UserDeletionConsumer userDeletionConsumer;

    @Autowired
    private RecordingIdentityAccounts recordingIdentityAccounts;

    @Test
    void deletingRemovesEveryRowOfTheUserAndNothingOfAnyoneElse() throws Exception {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        populate(user);
        populate(other);
        Map<String, Long> otherBefore = rowsPerTable(other);
        assertTrue(rowsPerTable(user).values().stream().allMatch(count -> count > 0),
                "every user_id table should have rows before: " + rowsPerTable(user));

        deleteAccount(user).andExpect(status().isNoContent());

        // Any table with a user_id column, also tables added after this test was written.
        Map<String, Long> left = rowsPerTable(user);
        assertEquals(1L, left.remove("outbox_event"), "only the deletion event stays in the outbox");
        assertTrue(left.values().stream().allMatch(count -> count == 0), "rows left: " + left);
        assertEquals(otherBefore, rowsPerTable(other));

        JsonNode event = outboxPayloads(user, "UserDeletionRequested").getFirst();
        EventSchemas.assertValidPayload("UserDeletionRequested", 1, event);

        JsonNode audit = auditDetails(user, "USER_DATA_DELETED");
        assertEquals(1, audit.at("/rows/user_profile").asInt());
        assertEquals(2, audit.at("/rows/strategy_version").asInt());
        assertEquals(2, audit.at("/rows/evidence").asInt());
        assertEquals(1, audit.at("/rows/change_proposal").asInt());
        assertEquals("USER", auditColumn(user, "USER_DATA_DELETED", "actor"));
        assertNotNull(auditColumn(user, "USER_DATA_DELETED", "correlation_id"));
    }

    @Test
    void theIdentityAccountIsDeletedAfterwardsThroughTheEvent() throws Exception {
        UUID user = UUID.randomUUID();
        mockMvc.perform(get("/api/strategy/me").with(as(user))).andExpect(status().isOk());

        deleteAccount(user).andExpect(status().isNoContent());

        awaitTrue(Duration.ofSeconds(30), () -> auditActions(user).contains("IDENTITY_ACCOUNT_DELETED"));
        assertEquals(List.of("USER_DATA_DELETED", "IDENTITY_ACCOUNT_DELETED"), auditActions(user));
        assertEquals("DELETED", auditDetails(user, "IDENTITY_ACCOUNT_DELETED").get("result").asString());
        assertEquals("SYSTEM", auditColumn(user, "IDENTITY_ACCOUNT_DELETED", "actor"));
        assertEquals(1, recordingIdentityAccounts.attempts(user));
    }

    @Test
    void anUnreachableIdentityProviderIsRetried() throws Exception {
        UUID user = UUID.randomUUID();
        recordingIdentityAccounts.failFor(user, 1);

        deleteAccount(user).andExpect(status().isNoContent());

        awaitTrue(Duration.ofSeconds(30), () -> auditActions(user).contains("IDENTITY_ACCOUNT_DELETED"));
        assertEquals(2, recordingIdentityAccounts.attempts(user));
        assertFalse(auditActions(user).contains("IDENTITY_ACCOUNT_DELETION_FAILED"));
    }

    @Test
    void aDeletionThatKeepsFailingEndsInTheAuditLog() throws Exception {
        UUID user = UUID.randomUUID();
        recordingIdentityAccounts.failFor(user, 100);

        deleteAccount(user).andExpect(status().isNoContent());

        // Three attempts (as configured for the tests), then the dead-letter topic.
        awaitTrue(Duration.ofSeconds(30),
                () -> auditActions(user).contains("IDENTITY_ACCOUNT_DELETION_FAILED"));
        assertEquals(List.of("USER_DATA_DELETED", "IDENTITY_ACCOUNT_DELETION_FAILED"), auditActions(user));
        assertEquals(3, recordingIdentityAccounts.attempts(user));
    }

    @Test
    void aTokenOfADeletedUserCannotStartOver() throws Exception {
        UUID user = UUID.randomUUID();
        mockMvc.perform(get("/api/strategy/me").with(as(user))).andExpect(status().isOk());
        deleteAccount(user).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/strategy/me").with(as(user)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("STRATEGY_USER_DELETED"));
        mockMvc.perform(post("/api/strategy/goals").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(goalBody()))
                .andExpect(status().isGone());
        assertEquals(0L, rowsPerTable(user).get("user_profile"));
        assertEquals(0L, rowsPerTable(user).get("goal"));
    }

    @Test
    void deletingAgainIsHarmless() throws Exception {
        UUID user = UUID.randomUUID();
        populate(user);

        deleteAccount(user).andExpect(status().isNoContent());
        deleteAccount(user).andExpect(status().isNoContent());

        // The second request found no data but is on record too. It replaced the first deletion event in the
        // outbox with its own, so the other services are told (again) exactly once more.
        List<JsonNode> deletions = jdbcClient.sql("""
                        SELECT details::text FROM audit_log
                        WHERE user_id = :userId AND action = 'USER_DATA_DELETED' ORDER BY occurred_at
                        """)
                .param("userId", user).query(String.class).list().stream().map(jsonMapper::readTree).toList();
        assertEquals(2, deletions.size());
        JsonNode second = deletions.getLast().get("rows");
        assertEquals(1, second.get("outbox_event").asInt());
        ((ObjectNode) second).remove("outbox_event");
        assertTrue(second.valueStream().allMatch(count -> count.asInt() == 0), "second deletion: " + second);
        assertEquals(1, outboxPayloads(user, "UserDeletionRequested").size());
    }

    @Test
    void theDeadLetterHandlerAuditsOnlyFailedAccountDeletions() throws Exception {
        UUID user = UUID.randomUUID();

        userDeletionConsumer.onDeadLetter(record(envelope(user, "GoalChanged")));
        userDeletionConsumer.onDeadLetter(record("not an envelope"));
        assertEquals(List.of(), auditActions(user));

        userDeletionConsumer.onDeadLetter(record(envelope(user, "UserDeletionRequested")));
        assertEquals(List.of("IDENTITY_ACCOUNT_DELETION_FAILED"), auditActions(user));
    }

    /**
     * Rows in every module, with the links that make the deletion order matter: a strategy revision that came from
     * a proposal (and the proposal pointing back at it), evidence building on evidence, a decision resting on a
     * knowledge item, a reviewed week with an action, an active idea.
     */
    private void populate(UUID user) throws Exception {
        String goal = id(postJson(user, "/api/strategy/goals", goalBody()));
        JsonNode version = jsonMapper.readTree(postJson(user, "/api/strategy/goals/" + goal + "/strategy-versions", """
                {"summary": "Portfolio first", "phases": [{"title": "Foundation", "startsOn": "2026-09-01",
                 "endsOn": "2026-11-30", "status": "ACTIVE"}], "effortAllocation": {"TECHNICAL": 100}}
                """).andReturn().getResponse().getContentAsString());
        String phaseKey = version.at("/phases/0/phaseKey").asString();
        String assumption = id(postJson(user, "/api/strategy/goals/" + goal + "/knowledge-items",
                "{\"statement\": \"Side projects count\", \"kind\": \"ASSUMPTION\"}"));
        String feedback = id(postJson(user, "/api/strategy/feedback", """
                {"sourceKind": "MANAGER", "content": "Practise system design", "reliability": "HIGH",
                 "receivedOn": "2026-10-01"}
                """));

        postJson(user, "/api/strategy/weekly-plans", """
                {"isoWeek": "%s", "capacityMinutes": 300, "allocations": [{"goalId": "%s", "allocatedMinutes": 200}]}
                """.formatted(W40, goal));
        String action = id(postJson(user, "/api/strategy/weekly-plans/" + W40 + "/actions", """
                {"title": "Decisions module", "kind": "TECHNICAL", "estimateMinutes": 60, "goalIds": ["%s"]}
                """.formatted(goal)));
        mockMvc.perform(post("/api/strategy/weekly-plans/" + W40 + "/activate").with(as(user)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/strategy/weekly-plans/" + W40 + "/actions/" + action + "/status").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 0, \"status\": \"DONE\"}"))
                .andExpect(status().isOk());
        String activity = id(postJson(user, "/api/strategy/evidence", """
                {"title": "Wrote it", "level": "ACTIVITY", "strength": "PERSONAL_PROJECT", "occurredOn": "2026-09-30",
                 "actionId": "%s", "phaseKeys": ["%s"]}
                """.formatted(action, phaseKey)));
        postJson(user, "/api/strategy/evidence", """
                {"title": "Praised in review", "level": "OUTCOME", "strength": "EXTERNAL_FEEDBACK",
                 "occurredOn": "2026-10-01", "parentEvidenceId": "%s", "feedbackId": "%s", "goalIds": ["%s"]}
                """.formatted(activity, feedback, goal));
        postJson(user, "/api/strategy/weekly-plans/" + W40 + "/review", "{\"energy\": 4}");

        String decision = id(postJson(user, "/api/strategy/decisions", """
                {"title": "Where to get K8s experience", "goalId": "%s", "assumptionIds": ["%s"],
                 "alternatives": [{"description": "Transfer", "rejectionReason": "Not in my hands"},
                                  {"description": "Own k3s server"}],
                 "reopenTriggers": [{"kind": "EVENT", "description": "If I get the K8s project"}]}
                """.formatted(goal, assumption)));
        mockMvc.perform(post("/api/strategy/decisions/" + decision + "/decide").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "chosenLabel": "B", "expectedOutcome": "K8s on my CV",
                                 "reviewOn": "%s"}
                                """.formatted(LocalDate.now().plusMonths(1))))
                .andExpect(status().isOk());

        String idea = id(postJson(user, "/api/strategy/ideas", "{\"title\": \"A CLI\", \"goalId\": \"" + goal + "\"}"));
        mockMvc.perform(post("/api/strategy/ideas/" + idea + "/evaluations").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goalFit": 4, "evidencePotential": 4, "energy": 5, "weeklyMinutes": 60,
                                 "verdict": "PURSUE"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/strategy/ideas/" + idea + "/activate").with(as(user))).andExpect(status().isOk());

        // Coach's proposal, accepted: version 2 points at the proposal, the proposal at version 2.
        ObjectNode recommendation = (ObjectNode) jsonMapper.readTree("""
                {"recommendationId": "%s", "kind": "STRATEGY_REVISION", "goalId": "%s", "basedOnVersionNo": 1,
                 "rationale": "Interviews ask for system design", "sources": [{"type": "FEEDBACK", "id": "%s"}],
                 "revision": {"summary": "Interviews earlier", "changeReason": "Manager feedback",
                   "phases": [{"phaseKey": "%s", "title": "Foundation", "startsOn": "2026-09-01",
                               "endsOn": "2026-11-30", "exitCriteria": null, "status": "ACTIVE"}],
                   "effortAllocation": {"TECHNICAL": 60, "DOMAIN": 40, "OTHER": 0}}}
                """.formatted(UUID.randomUUID(), goal, feedback, phaseKey));
        ObjectNode envelope = envelopeNode(user, "RecommendationProposed");
        envelope.set("payload", recommendation);
        coachEventConsumer.on(record(jsonMapper.writeValueAsString(envelope)));
        String proposal = jsonMapper.readTree(mockMvc.perform(get("/api/strategy/proposals").with(as(user)))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asString();
        mockMvc.perform(post("/api/strategy/proposals/" + proposal + "/accept").with(as(user)))
                .andExpect(status().isOk());
    }

    /** Rows of the user in every table that has a {@code user_id} column, except the audit log. */
    private Map<String, Long> rowsPerTable(UUID user) {
        List<String> tables = jdbcClient.sql("""
                        SELECT table_name FROM information_schema.columns
                        WHERE table_schema = 'public' AND column_name = 'user_id' AND table_name <> 'audit_log'
                        ORDER BY table_name
                        """)
                .query(String.class).list();
        Map<String, Long> rows = new LinkedHashMap<>();
        for (String table : tables) {
            rows.put(table, jdbcClient.sql("SELECT count(*) FROM " + table + " WHERE user_id = :userId")
                    .param("userId", user).query(Long.class).single());
        }
        return rows;
    }

    private List<String> auditActions(UUID user) {
        return jdbcClient.sql("SELECT action FROM audit_log WHERE user_id = :userId ORDER BY occurred_at")
                .param("userId", user).query(String.class).list();
    }

    private JsonNode auditDetails(UUID user, String action) {
        return jsonMapper.readTree(auditColumn(user, action, "details::text"));
    }

    private String auditColumn(UUID user, String action, String column) {
        return jdbcClient.sql("SELECT " + column + " FROM audit_log WHERE user_id = :userId AND action = :action")
                .param("userId", user).param("action", action).query(String.class).single();
    }

    private ResultActions deleteAccount(UUID user) throws Exception {
        return mockMvc.perform(delete("/api/strategy/me").with(as(user)));
    }

    private ResultActions postJson(UUID user, String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful());
    }

    private String id(ResultActions created) throws Exception {
        return jsonMapper.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    private static String goalBody() {
        return """
                {"title": "In-house role", "domain": "CAREER", "windowStart": "2026-09-01", "windowEnd": "2027-06-30"}
                """;
    }

    private ObjectNode envelopeNode(UUID user, String eventType) {
        return jsonMapper.createObjectNode()
                .put("eventId", UuidV7.next().toString())
                .put("eventType", eventType)
                .put("eventVersion", 1)
                .put("occurredAt", Instant.now().toString())
                .put("userId", user.toString());
    }

    private String envelope(UUID user, String eventType) {
        ObjectNode envelope = envelopeNode(user, eventType);
        envelope.set("payload", jsonMapper.createObjectNode().put("requestedAt", Instant.now().toString()));
        return jsonMapper.writeValueAsString(envelope);
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("lifeos.strategy.events.v1-dlt", 0, 0L, null, value);
    }
}

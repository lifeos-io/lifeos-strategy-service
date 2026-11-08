package com.lifeos.strategy.proposal;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifeos.base.id.UuidV7;
import com.lifeos.base.messaging.MalformedEventException;
import com.lifeos.strategy.proposal.consumer.CoachEventConsumer;
import com.lifeos.strategy.support.EventSchemas;
import com.lifeos.strategy.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Recommendations are mostly fed to the consumer directly (one record, synchronously); one test sends a real
 * event through Kafka.
 */
class ChangeProposalIntegrationTest extends IntegrationTest {

    private static final String COACH_TOPIC = "lifeos.coach.events.v1";

    @Autowired
    private CoachEventConsumer coachEventConsumer;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void aRecommendationBecomesAPendingProposal() throws Exception {
        Setup setup = setup();
        UUID recommendation = UUID.randomUUID();
        JsonNode payload = recommendation(setup, recommendation, 1);
        EventSchemas.assertValidCoachPayload("RecommendationProposed", 1, payload);

        deliver(setup.user(), payload);

        String proposal = onlyProposalId(setup);
        mockMvc.perform(get(proposals() + "/" + proposal).with(as(setup.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationId").value(recommendation.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.basedOnVersionNo").value(1))
                .andExpect(jsonPath("$.rationale").value("Interviews ask for system design"))
                .andExpect(jsonPath("$.sources[0].type").value("FEEDBACK"))
                .andExpect(jsonPath("$.revision.phases[*].title")
                        .value(contains("Build the foundation", "Practise system design")))
                .andExpect(jsonPath("$.revision.phases[0].phaseKey").value(setup.phaseKey()))
                .andExpect(jsonPath("$.revision.effortAllocation.DOMAIN").value(30));
        // Nothing is decided yet, and the plan is unchanged.
        assertEquals(List.of(), outboxPayloads(setup.user(), "ProposalDecided"));
        assertEquals(1, outboxPayloads(setup.user(), "StrategyRevised").size());
    }

    @Test
    void acceptingAppliesTheRevisionAsTheNewStrategyVersion() throws Exception {
        Setup setup = setup();
        deliver(setup.user(), recommendation(setup, UUID.randomUUID(), 1));
        String proposal = onlyProposalId(setup);

        String versionId = jsonMapper.readTree(accept(setup, proposal)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("ACCEPTED"))
                        .andExpect(jsonPath("$.applicable").value(false))
                        .andExpect(jsonPath("$.decidedAt").isNotEmpty())
                        .andReturn().getResponse().getContentAsString())
                .get("appliedStrategyVersionId").asString();

        mockMvc.perform(get("/api/strategy/goals/" + setup.goal() + "/strategy-versions/current")
                        .with(as(setup.user())))
                .andExpect(jsonPath("$.id").value(versionId))
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.sourceProposalId").value(proposal))
                .andExpect(jsonPath("$.changeReason").value("Manager feedback on interviews"))
                // The carried-over phase keeps its key.
                .andExpect(jsonPath("$.phases[0].phaseKey").value(setup.phaseKey()));

        JsonNode revised = outboxPayloads(setup.user(), "StrategyRevised").getLast();
        assertEquals(proposal, revised.get("sourceProposalId").asString());
        JsonNode decided = outboxPayloads(setup.user(), "ProposalDecided").getFirst();
        EventSchemas.assertValidPayload("ProposalDecided", 1, decided);
        assertEquals("ACCEPTED", decided.get("decision").asString());
        assertEquals(versionId, decided.get("strategyVersionId").asString());
    }

    @Test
    void rejectingRecordsTheReasonForCoach() throws Exception {
        Setup setup = setup();
        UUID recommendation = UUID.randomUUID();
        deliver(setup.user(), recommendation(setup, recommendation, 1));
        String proposal = onlyProposalId(setup);

        mockMvc.perform(post(proposals() + "/" + proposal + "/reject").with(as(setup.user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"  I learn system design at work already  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value("I learn system design at work already"));

        JsonNode decided = outboxPayloads(setup.user(), "ProposalDecided").getFirst();
        EventSchemas.assertValidPayload("ProposalDecided", 1, decided);
        assertEquals("REJECTED", decided.get("decision").asString());
        assertEquals(recommendation.toString(), decided.get("recommendationId").asString());
        assertTrue(decided.get("strategyVersionId").isNull());
        assertEquals(1, outboxPayloads(setup.user(), "StrategyRevised").size());

        // A rejection needs no body.
        deliver(setup.user(), recommendation(setup, UUID.randomUUID(), 1));
        String second = pendingProposalId(setup);
        mockMvc.perform(post(proposals() + "/" + second + "/reject").with(as(setup.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionNote").isEmpty());
    }

    @Test
    void aNewerRecommendationSupersedesThePendingOne() throws Exception {
        Setup setup = setup();
        deliver(setup.user(), recommendation(setup, UUID.randomUUID(), 1));
        String first = onlyProposalId(setup);
        deliver(setup.user(), recommendation(setup, UUID.randomUUID(), 1));

        mockMvc.perform(get(proposals()).with(as(setup.user())))
                .andExpect(jsonPath("$[*].status").value(contains("PENDING", "SUPERSEDED")))
                .andExpect(jsonPath("$[1].id").value(first));
        JsonNode decided = outboxPayloads(setup.user(), "ProposalDecided").getFirst();
        EventSchemas.assertValidPayload("ProposalDecided", 1, decided);
        assertEquals("SUPERSEDED", decided.get("decision").asString());
        accept(setup, first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_PROPOSAL_STATUS"))
                .andExpect(jsonPath("$.currentStatus").value("SUPERSEDED"));
    }

    @Test
    void aProposalBasedOnAnOlderStrategyCannotBeAccepted() throws Exception {
        Setup setup = setup();
        deliver(setup.user(), recommendation(setup, UUID.randomUUID(), 1));
        String proposal = onlyProposalId(setup);

        // The user revises the strategy themselves in the meantime.
        mockMvc.perform(post("/api/strategy/goals/" + setup.goal() + "/strategy-versions").with(as(setup.user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedCurrentVersionNo": 1, "summary": "My own revision",
                                 "changeReason": "Changed my mind",
                                 "phases": [{"phaseKey": "%s", "title": "Build the foundation",
                                             "startsOn": "2026-09-01", "endsOn": "2026-11-30", "status": "ACTIVE"}],
                                 "effortAllocation": {"TECHNICAL": 100}}
                                """.formatted(setup.phaseKey())))
                .andExpect(status().isCreated());

        mockMvc.perform(get(proposals() + "/" + proposal).with(as(setup.user())))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.applicable").value(false));
        accept(setup, proposal)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_PROPOSAL_OUTDATED"))
                .andExpect(jsonPath("$.basedOnVersionNo").value(1))
                .andExpect(jsonPath("$.currentVersionNo").value(2));
        mockMvc.perform(post(proposals() + "/" + proposal + "/reject").with(as(setup.user())))
                .andExpect(status().isOk());
    }

    @Test
    void aRevisionThatBreaksTheGoalsRulesIsReportedOnAccept() throws Exception {
        Setup setup = setup();
        ObjectNode payload = recommendation(setup, UUID.randomUUID(), 1);
        // Valid on its own, but the phase ends after the goal does (checked against the goal on accept).
        ((ObjectNode) payload.at("/revision/phases/1")).put("endsOn", "2027-12-31");
        deliver(setup.user(), payload);

        accept(setup, onlyProposalId(setup))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phases[1].endsOn"));
        mockMvc.perform(get(proposals()).with(as(setup.user())))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void repeatedAndIrrelevantRecommendationsAreSkipped() throws Exception {
        Setup setup = setup();
        UUID recommendation = UUID.randomUUID();
        ObjectNode payload = recommendation(setup, recommendation, 1);
        String envelope = envelope(setup.user(), "RecommendationProposed", payload);

        // The same event twice (inbox), and the same recommendation in a new event.
        coachEventConsumer.on(record(setup.user(), envelope));
        coachEventConsumer.on(record(setup.user(), envelope));
        deliver(setup.user(), payload);
        assertEquals(1, proposalCount(setup.user()));

        // Another user's goal, a closed goal, and a Coach event Strategy does not use.
        Setup other = setup();
        deliver(setup.user(), recommendation(other, UUID.randomUUID(), 1));
        mockMvc.perform(patch("/api/strategy/goals/" + other.goal()).with(as(other.user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 0, \"status\": \"ABANDONED\"}"))
                .andExpect(status().isOk());
        deliver(other.user(), recommendation(other, UUID.randomUUID(), 1));
        coachEventConsumer.on(record(setup.user(), envelope(setup.user(), "ConversationStarted",
                jsonMapper.createObjectNode().put("conversationId", UUID.randomUUID().toString()))));

        assertEquals(1, proposalCount(setup.user()));
        assertEquals(0, proposalCount(other.user()));
    }

    @Test
    void aMalformedRecommendationIsRejectedWithoutSideEffects() throws Exception {
        Setup setup = setup();
        ObjectNode blankTitle = recommendation(setup, UUID.randomUUID(), 1);
        ((ObjectNode) blankTitle.at("/revision/phases/0")).put("title", " ");
        ObjectNode unknownKind = recommendation(setup, UUID.randomUUID(), 1).put("kind", "WEEKLY_PLAN_CHANGE");
        String envelope = envelope(setup.user(), "RecommendationProposed", blankTitle);

        assertThrows(MalformedEventException.class, () -> coachEventConsumer.on(record(setup.user(), envelope)));
        assertThrows(MalformedEventException.class, () -> coachEventConsumer.on(record(setup.user(),
                envelope(setup.user(), "RecommendationProposed", unknownKind))));

        assertEquals(0, proposalCount(setup.user()));
        // Rolled back with the handler, so the event is not marked processed.
        assertEquals(0, jdbcClient.sql("SELECT count(*) FROM inbox_message WHERE message_id = :id")
                .param("id", UUID.fromString(jsonMapper.readTree(envelope).get("eventId").asString()))
                .query(Long.class).single());
    }

    @Test
    void proposalsAreListedAndScopedToTheUser() throws Exception {
        Setup setup = setup();
        deliver(setup.user(), recommendation(setup, UUID.randomUUID(), 1));
        String proposal = onlyProposalId(setup);

        mockMvc.perform(get(proposals()).param("status", "ACCEPTED").with(as(setup.user())))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(proposals()).param("goalId", setup.goal()).with(as(setup.user())))
                .andExpect(jsonPath("$[*].id").value(contains(proposal)));
        UUID stranger = UUID.randomUUID();
        mockMvc.perform(get(proposals() + "/" + proposal).with(as(stranger))).andExpect(status().isNotFound());
        accept(new Setup(stranger, setup.goal(), setup.phaseKey()), proposal).andExpect(status().isNotFound());
        mockMvc.perform(get(proposals()).with(as(stranger))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aRecommendationArrivesThroughKafka() throws Exception {
        Setup setup = setup();
        UUID recommendation = UUID.randomUUID();
        kafkaTemplate.send(COACH_TOPIC, setup.user().toString(),
                envelope(setup.user(), "RecommendationProposed", recommendation(setup, recommendation, 1))).get();

        awaitTrue(Duration.ofSeconds(30), () -> proposalCount(setup.user()) == 1);
        mockMvc.perform(get(proposals()).with(as(setup.user())))
                .andExpect(jsonPath("$[0].recommendationId").value(recommendation.toString()));
    }

    /** A user with a goal and its first strategy (one phase). */
    private Setup setup() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = jsonMapper.readTree(mockMvc.perform(post("/api/strategy/goals").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "In-house role", "domain": "CAREER",
                                 "windowStart": "2026-09-01", "windowEnd": "2027-06-30"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asString();
        JsonNode version = jsonMapper.readTree(mockMvc.perform(post("/api/strategy/goals/" + goal
                        + "/strategy-versions").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"summary": "Portfolio first, then apply",
                                 "phases": [{"title": "Build the foundation", "startsOn": "2026-09-01",
                                             "endsOn": "2026-11-30", "status": "ACTIVE"}],
                                 "effortAllocation": {"TECHNICAL": 70, "DOMAIN": 20, "OTHER": 10}}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return new Setup(user, goal, version.at("/phases/0/phaseKey").asString());
    }

    /** The mock data's revision: keep the foundation phase, add interview practice, more domain effort. */
    private ObjectNode recommendation(Setup setup, UUID recommendationId, Integer basedOnVersionNo) {
        return (ObjectNode) jsonMapper.readTree("""
                {"recommendationId": "%s", "kind": "STRATEGY_REVISION", "goalId": "%s", "basedOnVersionNo": %s,
                 "rationale": "Interviews ask for system design",
                 "sources": [{"type": "FEEDBACK", "id": "%s"}],
                 "revision": {
                   "summary": "Portfolio first, but start interviews earlier",
                   "changeReason": "Manager feedback on interviews",
                   "phases": [
                     {"phaseKey": "%s", "title": "Build the foundation", "startsOn": "2026-09-01",
                      "endsOn": "2026-11-30", "exitCriteria": null, "status": "ACTIVE"},
                     {"phaseKey": null, "title": "Practise system design", "startsOn": "2026-12-01",
                      "endsOn": "2027-02-28", "exitCriteria": "Three mock interviews", "status": "UPCOMING"}],
                   "effortAllocation": {"TECHNICAL": 55, "DOMAIN": 30, "OTHER": 15}}}
                """.formatted(recommendationId, setup.goal(), basedOnVersionNo, UUID.randomUUID(),
                setup.phaseKey()));
    }

    /** Hands a new event with {@code payload} to the consumer, as Kafka would. */
    private void deliver(UUID user, JsonNode payload) {
        coachEventConsumer.on(record(user, envelope(user, "RecommendationProposed", payload)));
    }

    private String envelope(UUID user, String eventType, JsonNode payload) {
        ObjectNode envelope = jsonMapper.createObjectNode()
                .put("eventId", UuidV7.next().toString())
                .put("eventType", eventType)
                .put("eventVersion", 1)
                .put("occurredAt", Instant.now().toString())
                .put("userId", user.toString())
                .put("correlationId", "test-" + eventType);
        envelope.set("payload", payload);
        EventSchemas.assertValidEnvelope(envelope);
        return jsonMapper.writeValueAsString(envelope);
    }

    private static ConsumerRecord<String, String> record(UUID user, String envelope) {
        return new ConsumerRecord<>(COACH_TOPIC, 0, 0L, user.toString(), envelope);
    }

    private ResultActions accept(Setup setup, String proposalId) throws Exception {
        return mockMvc.perform(post(proposals() + "/" + proposalId + "/accept").with(as(setup.user())));
    }

    private String onlyProposalId(Setup setup) throws Exception {
        JsonNode list = jsonMapper.readTree(mockMvc.perform(get(proposals()).with(as(setup.user())))
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, list.size());
        return list.get(0).get("id").asString();
    }

    private String pendingProposalId(Setup setup) throws Exception {
        JsonNode list = jsonMapper.readTree(mockMvc.perform(get(proposals()).param("status", "PENDING")
                .with(as(setup.user()))).andReturn().getResponse().getContentAsString());
        assertFalse(list.isEmpty());
        return list.get(0).get("id").asString();
    }

    private long proposalCount(UUID user) {
        return jdbcClient.sql("SELECT count(*) FROM change_proposal WHERE user_id = :user")
                .param("user", user).query(Long.class).single();
    }

    private static void awaitTrue(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("condition not met within " + timeout);
            }
            Thread.sleep(100);
        }
    }

    private static String proposals() {
        return "/api/strategy/proposals";
    }

    private record Setup(UUID user, String goal, String phaseKey) {
    }
}

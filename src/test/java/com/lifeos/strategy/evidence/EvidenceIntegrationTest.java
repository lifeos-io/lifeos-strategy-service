package com.lifeos.strategy.evidence;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifeos.strategy.support.EventSchemas;
import com.lifeos.strategy.support.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class EvidenceIntegrationTest extends IntegrationTest {

    private static final String EVENT = "EvidenceRecorded";

    @Test
    void evidenceOfAnActionServesTheActionsGoalsAndCountsTowardsAPhase() throws Exception {
        Setup setup = setup();

        record(setup.user(), """
                {"title": "Gateway rate limit written", "level": "ACTIVITY", "strength": "PERSONAL_PROJECT",
                 "occurredOn": "2026-11-05", "actionId": "%s", "phaseKeys": ["%s"], "note": "private context"}
                """.formatted(setup.action(), setup.phaseKey()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.goalIds[0]").value(setup.goal()))
                .andExpect(jsonPath("$.phaseKeys[0]").value(setup.phaseKey()))
                .andExpect(jsonPath("$.actionId").value(setup.action()));

        List<JsonNode> events = outboxPayloads(setup.user(), EVENT);
        assertEquals(1, events.size());
        EventSchemas.assertValidPayload(EVENT, 1, events.getFirst());
        assertEquals(setup.goal(), events.getFirst().at("/goalIds/0").asString());
    }

    @Test
    void theChainOnlyGrowsUpwards() throws Exception {
        Setup setup = setup();
        String activity = id(record(setup.user(), simple("Rate limit written", "ACTIVITY", null, setup.goal())));
        String output = id(record(setup.user(), simple("lifeos-gateway 0.1.0 released", "OUTPUT", activity, setup.goal()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentEvidenceId").value(activity)));

        record(setup.user(), simple("Another activity", "ACTIVITY", output, setup.goal()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parentEvidenceId"));
        record(setup.user(), simple("Same level", "OUTPUT", output, setup.goal()))
                .andExpect(status().isBadRequest());
        record(UUID.randomUUID(), simple("Someone else's parent", "OUTCOME", output, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parentEvidenceId"));
    }

    @Test
    void feedbackCanBackAnOutcome() throws Exception {
        Setup setup = setup();
        String feedback = jsonMapper.readTree(mockMvc.perform(post("/api/strategy/feedback").with(as(setup.user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceKind": "CODE_REVIEW", "content": "Clean outbox", "reliability": "MEDIUM",
                                 "receivedOn": "2026-11-04"}
                                """))
                .andReturn().getResponse().getContentAsString()).get("id").asString();

        record(setup.user(), """
                {"title": "Outbox praised in review", "level": "OUTCOME", "strength": "EXTERNAL_FEEDBACK",
                 "occurredOn": "2026-11-04", "feedbackId": "%s", "goalIds": ["%s"]}
                """.formatted(feedback, setup.goal()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.feedbackId").value(feedback));
        record(UUID.randomUUID(), """
                {"title": "Not my feedback", "level": "OUTCOME", "strength": "EXTERNAL_FEEDBACK",
                 "occurredOn": "2026-11-04", "feedbackId": "%s"}
                """.formatted(feedback))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("feedbackId"));
    }

    @Test
    void linksMustBeTheUsersAndDatesNotInTheFuture() throws Exception {
        Setup setup = setup();
        Setup other = setup();

        String response = record(setup.user(), """
                {"title": "Bad links", "level": "ACTIVITY", "strength": "SELF_REPORT", "occurredOn": "%s",
                 "actionId": "%s", "goalIds": ["%s"], "phaseKeys": ["%s"]}
                """.formatted(LocalDate.now().plusDays(2), other.action(), other.goal(), UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        List<String> fields = jsonMapper.readTree(response).get("errors").valueStream()
                .map(error -> error.get("field").asString()).toList();
        assertEquals(List.of("occurredOn", "actionId", "goalIds", "phaseKeys"), fields);
        record(setup.user(), """
                {"title": "Phase without goal", "level": "ACTIVITY", "strength": "SELF_REPORT",
                 "occurredOn": "2026-11-05", "phaseKeys": ["%s"]}
                """.formatted(setup.phaseKey()))
                .andExpect(jsonPath("$.errors[0].field").value("phaseKeys"));
    }

    @Test
    void listFiltersByGoalAndLevelNewestFirst() throws Exception {
        Setup setup = setup();
        String secondGoal = createGoal(setup.user(), "Company");
        record(setup.user(), simple("Old activity", "ACTIVITY", null, setup.goal()));
        record(setup.user(), simple("New activity", "ACTIVITY", null, setup.goal()).replace("2026-11-05", "2026-11-07"));
        record(setup.user(), simple("Company output", "OUTPUT", null, secondGoal));

        mockMvc.perform(get("/api/strategy/evidence").param("goalId", setup.goal()).with(as(setup.user())))
                .andExpect(jsonPath("$[*].title").value(contains("New activity", "Old activity")));
        mockMvc.perform(get("/api/strategy/evidence").param("level", "OUTPUT").with(as(setup.user())))
                .andExpect(jsonPath("$[*].title").value(contains("Company output")));
        mockMvc.perform(get("/api/strategy/evidence").with(as(UUID.randomUUID())))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** A user with a goal (strategy with one phase) and an action in a W46 draft plan. */
    private Setup setup() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user, "Career");
        String strategy = """
                {"summary": "Portfolio first", "effortAllocation": {"TECHNICAL": 100},
                 "phases": [{"title": "Visibility", "startsOn": "2026-11-01", "endsOn": "2026-12-31",
                             "status": "ACTIVE"}]}
                """;
        String phaseKey = body(mockMvc.perform(post("/api/strategy/goals/" + goal + "/strategy-versions")
                .with(as(user)).contentType(MediaType.APPLICATION_JSON).content(strategy)))
                .at("/phases/0/phaseKey").asString();
        mockMvc.perform(post("/api/strategy/weekly-plans").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"isoWeek": "2026-W46", "capacityMinutes": 540,
                         "allocations": [{"goalId": "%s", "allocatedMinutes": 240}]}
                        """.formatted(goal)));
        String action = id(mockMvc.perform(post("/api/strategy/weekly-plans/2026-W46/actions").with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "Rate limit piece", "kind": "TECHNICAL", "estimateMinutes": 120,
                         "goalIds": ["%s"]}
                        """.formatted(goal))));
        return new Setup(user, goal, phaseKey, action);
    }

    private ResultActions record(UUID user, String body) throws Exception {
        return mockMvc.perform(post("/api/strategy/evidence").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String simple(String title, String level, String parent, String goal) {
        return """
                {"title": "%s", "level": "%s", "strength": "PERSONAL_PROJECT", "occurredOn": "2026-11-05",
                 "parentEvidenceId": %s, "goalIds": %s}
                """.formatted(title, level, parent == null ? "null" : "\"" + parent + "\"",
                goal == null ? "[]" : "[\"" + goal + "\"]");
    }

    private String createGoal(UUID user, String title) throws Exception {
        String body = """
                {"title": "%s", "domain": "CAREER", "windowStart": "2026-09-01", "windowEnd": "2027-06-30"}
                """.formatted(title);
        return id(mockMvc.perform(post("/api/strategy/goals").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String id(ResultActions created) throws Exception {
        return body(created).get("id").asString();
    }

    private record Setup(UUID user, String goal, String phaseKey, String action) {
    }
}

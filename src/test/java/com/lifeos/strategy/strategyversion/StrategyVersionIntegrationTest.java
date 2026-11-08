package com.lifeos.strategy.strategyversion;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifeos.strategy.support.EventSchemas;
import com.lifeos.strategy.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class StrategyVersionIntegrationTest extends IntegrationTest {

    private static final String EVENT = "StrategyRevised";

    /** v1 of the mock data: three phases inside the goal window 2026-09-01 .. 2027-06-30. */
    private static final String V1_PHASES = """
            [{"title": "Build the foundation", "startsOn": "2026-09-01", "endsOn": "2026-10-31",
              "exitCriteria": "Backend green in CI, 1 blog post", "status": "DONE"},
             {"title": "Visibility", "startsOn": "2026-11-01", "endsOn": "2026-12-31",
              "exitCriteria": "3 posts, 2 released services", "status": "ACTIVE"},
             {"title": "Application wave", "startsOn": "2027-01-01", "endsOn": "2027-03-31",
              "exitCriteria": "15 applications, 5 interviews", "status": "UPCOMING"}]
            """;

    @Test
    void theFirstStrategyIsVersionOneWithNewPhaseKeys() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);

        create(user, goal, null, "Portfolio first, then applications", null, V1_PHASES, 60, 20, 20)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/strategy/goals/" + goal + "/strategy-versions/1"))
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.changeReason").value("Initial strategy"))
                .andExpect(jsonPath("$.phases[*].ordinal").value(contains(1, 2, 3)))
                .andExpect(jsonPath("$.phases[0].phaseKey").isNotEmpty())
                .andExpect(jsonPath("$.effortAllocation.TECHNICAL").value(60));

        List<JsonNode> events = outboxPayloads(user, EVENT);
        assertEquals(1, events.size());
        EventSchemas.assertValidPayload(EVENT, 1, events.getFirst());
        assertTrue(events.getFirst().get("previousVersionNo").isNull());
    }

    @Test
    void aRevisionCarriesPhasesOverByKeyAndKeepsTheOldVersionAsHistory() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);
        JsonNode v1 = body(create(user, goal, null, "Portfolio first", null, V1_PHASES, 60, 20, 20));
        String foundation = v1.at("/phases/0/phaseKey").asString();
        String visibility = v1.at("/phases/1/phaseKey").asString();

        String v2Phases = """
                [{"phaseKey": "%s", "title": "Build the foundation", "startsOn": "2026-09-01", "endsOn": "2026-10-31",
                  "status": "DONE"},
                 {"phaseKey": "%s", "title": "Visibility + K8s evidence", "startsOn": "2026-11-01",
                  "endsOn": "2027-01-15", "exitCriteria": "Life OS running on k3s", "status": "ACTIVE"},
                 {"title": "Application wave", "startsOn": "2027-01-16", "endsOn": "2027-04-15", "status": "UPCOMING"}]
                """.formatted(foundation, visibility);
        create(user, goal, 1, "Add K8s evidence, apply 2 weeks later", "Manager: K8s experience is missing",
                v2Phases, 50, 30, 20)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.phases[0].phaseKey").value(foundation))
                .andExpect(jsonPath("$.phases[1].phaseKey").value(visibility))
                .andExpect(jsonPath("$.phases[1].title").value("Visibility + K8s evidence"));

        mockMvc.perform(get(versions(goal) + "/current").with(as(user)))
                .andExpect(jsonPath("$.versionNo").value(2));
        mockMvc.perform(get(versions(goal) + "/1").with(as(user)))
                .andExpect(jsonPath("$.current").value(false))
                .andExpect(jsonPath("$.phases[1].title").value("Visibility"))
                .andExpect(jsonPath("$.effortAllocation.TECHNICAL").value(60));
        mockMvc.perform(get(versions(goal)).with(as(user)))
                .andExpect(jsonPath("$[*].versionNo").value(contains(2, 1)));

        JsonNode revised = outboxPayloads(user, EVENT).getLast();
        EventSchemas.assertValidPayload(EVENT, 1, revised);
        assertEquals(1, revised.get("previousVersionNo").asInt());
        assertEquals(visibility, revised.at("/phases/1/phaseKey").asString());
    }

    @Test
    void aRevisionBasedOnAnOldVersionIsRejected() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);
        create(user, goal, null, "v1", null, V1_PHASES, 60, 20, 20).andExpect(status().isCreated());
        create(user, goal, 1, "v2", "New info", V1_PHASES, 50, 30, 20).andExpect(status().isCreated());

        create(user, goal, 1, "Also v2", "Based on v1", V1_PHASES, 40, 40, 20)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.currentVersionNo").value(2));
        create(user, createGoal(user), 1, "No v1 exists", "x", V1_PHASES, 60, 20, 20)
                .andExpect(status().isConflict());
    }

    @Test
    void rulesAreReportedPerField() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);
        create(user, goal, null, "v1", null, V1_PHASES, 60, 20, 20).andExpect(status().isCreated());

        String badPhases = """
                [{"phaseKey": "%s", "title": "Unknown key", "startsOn": "2026-08-01", "endsOn": "2026-09-30",
                  "status": "ACTIVE"},
                 {"title": "Past the goal", "startsOn": "2027-05-01", "endsOn": "2027-08-01", "status": "ACTIVE"}]
                """.formatted(UUID.randomUUID());
        String response = create(user, goal, 1, "v2", " ", badPhases, 50, 30, 10)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn().getResponse().getContentAsString();

        List<String> fields = jsonMapper.readTree(response).get("errors").valueStream()
                .map(error -> error.get("field").asString()).toList();
        assertEquals(List.of("changeReason", "effortAllocation", "phases[0].phaseKey", "phases[0].startsOn",
                "phases[1].endsOn", "phases"), fields);
    }

    @Test
    void closedGoalsAndOtherUsersGoalsAreOffLimits() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);

        mockMvc.perform(get(versions(goal) + "/current").with(as(user)))
                .andExpect(status().isNotFound());
        create(UUID.randomUUID(), goal, null, "Not mine", null, V1_PHASES, 60, 20, 20)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(patch("/api/strategy/goals/" + goal).with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0, \"status\": \"ABANDONED\"}"))
                .andExpect(status().isOk());
        create(user, goal, null, "Too late", null, V1_PHASES, 60, 20, 20)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_GOAL_CLOSED"));
    }

    @Test
    void effortAllocationAlwaysListsEveryCategory() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);
        String body = """
                {"summary": "All technical", "phases": %s, "effortAllocation": {"TECHNICAL": 100}}
                """.formatted(V1_PHASES);

        mockMvc.perform(post(versions(goal)).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effortAllocation.TECHNICAL").value(100))
                .andExpect(jsonPath("$.effortAllocation.DOMAIN").value(0))
                .andExpect(jsonPath("$.effortAllocation.OTHER").value(0));
        assertEquals(List.of("DOMAIN", "OTHER", "TECHNICAL"), outboxPayloads(user, EVENT).getFirst()
                .get("effortAllocation").propertyNames().stream().sorted().toList());
    }

    private ResultActions create(UUID user, String goalId, Integer expected, String summary, String changeReason,
            String phases, int technical, int domain, int other) throws Exception {
        String body = """
                {"expectedCurrentVersionNo": %s, "summary": "%s", "changeReason": %s, "phases": %s,
                 "effortAllocation": {"TECHNICAL": %d, "DOMAIN": %d, "OTHER": %d}}
                """.formatted(expected, summary, changeReason == null ? "null" : "\"" + changeReason + "\"", phases,
                technical, domain, other);
        return mockMvc.perform(post(versions(goalId)).with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createGoal(UUID user) throws Exception {
        String body = """
                {"title": "Move to an in-house backend role", "domain": "CAREER",
                 "windowStart": "2026-09-01", "windowEnd": "2027-06-30"}
                """;
        return body(mockMvc.perform(post("/api/strategy/goals").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body))).get("id").asString();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String versions(String goalId) {
        return "/api/strategy/goals/" + goalId + "/strategy-versions";
    }
}

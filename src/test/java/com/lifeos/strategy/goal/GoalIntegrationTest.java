package com.lifeos.strategy.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

class GoalIntegrationTest extends IntegrationTest {

    private static final String EVENT = "GoalChanged";

    @Test
    void newGoalsAreActiveAndQueueUpByPriority() throws Exception {
        UUID user = UUID.randomUUID();

        JsonNode first = create(user, "Move to an in-house backend role")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/strategy/goals/")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.priority").value(1))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString().transform(jsonMapper::readTree);
        create(user, "Reach the first 10 Life OS users").andExpect(jsonPath("$.priority").value(2));

        List<JsonNode> events = outboxPayloads(user, EVENT);
        assertEquals(2, events.size());
        EventSchemas.assertValidPayload(EVENT, 1, events.getFirst());
        assertEquals(first.get("id").asString(), events.getFirst().get("goalId").asString());
        assertEquals(1, jdbcClient.sql("SELECT count(*) FROM user_profile WHERE user_id = :id")
                .param("id", user).query(Integer.class).single(), "the first goal creates the profile");
    }

    @Test
    void listIsOrderedByPriorityAndFiltersByStatus() throws Exception {
        UUID user = UUID.randomUUID();
        String a = id(create(user, "A"));
        create(user, "B");
        update(user, a, 0, "{\"status\": \"PAUSED\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/strategy/goals").with(as(user)))
                .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.contains("A", "B")));
        mockMvc.perform(get("/api/strategy/goals").param("status", "ACTIVE").with(as(user)))
                .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.contains("B")));
    }

    @Test
    void patchChangesTheGivenFieldsAndBumpsTheVersionInResponseAndEvent() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = id(create(user, "Old title"));

        update(user, goal, 0, "{\"title\": \"New title\", \"windowEnd\": \"2027-12-31\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.windowEnd").value("2027-12-31"))
                .andExpect(jsonPath("$.domain").value("CAREER"))
                .andExpect(jsonPath("$.version").value(1));
        // Same values again: no change, no event, version stays.
        update(user, goal, 1, "{\"title\": \"New title\"}").andExpect(jsonPath("$.version").value(1));

        List<JsonNode> events = outboxPayloads(user, EVENT);
        assertEquals(2, events.size());
        EventSchemas.assertValidPayload(EVENT, 1, events.getLast());
        assertEquals(1, events.getLast().get("version").asInt());
        assertEquals("New title", events.getLast().get("title").asString());
    }

    @Test
    void aStaleVersionIsRejected() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = id(create(user, "Goal"));
        update(user, goal, 0, "{\"title\": \"First edit\"}").andExpect(status().isOk());

        update(user, goal, 0, "{\"title\": \"Edit based on an old copy\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.currentVersion").value(1));
    }

    @Test
    void statusFollowsItsLifecycleAndClosedGoalsAreReadOnly() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = id(create(user, "Goal"));

        update(user, goal, 0, "{\"status\": \"PAUSED\"}").andExpect(jsonPath("$.status").value("PAUSED"));
        update(user, goal, 1, "{\"status\": \"ACTIVE\"}").andExpect(jsonPath("$.status").value("ACTIVE"));
        update(user, goal, 2, "{\"status\": \"ACHIEVED\"}").andExpect(jsonPath("$.status").value("ACHIEVED"));

        update(user, goal, 3, "{\"status\": \"ACTIVE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_GOAL_STATUS_TRANSITION"));
        update(user, goal, 3, "{\"title\": \"Rename after the fact\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_GOAL_CLOSED"));
        assertEquals(4, outboxPayloads(user, EVENT).size(), "created + 3 status changes");
    }

    @Test
    void theWindowMustNotEndBeforeItStarts() throws Exception {
        UUID user = UUID.randomUUID();
        String body = """
                {"title": "Goal", "domain": "CAREER", "windowStart": "2026-09-01", "windowEnd": "2026-08-01"}
                """;
        mockMvc.perform(post("/api/strategy/goals").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("windowEnd"));

        String goal = id(create(user, "Goal"));
        update(user, goal, 0, "{\"windowStart\": \"2028-01-01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("windowEnd"));
    }

    @Test
    void anotherUsersGoalIsNotFound() throws Exception {
        String goal = id(create(UUID.randomUUID(), "Private goal"));

        mockMvc.perform(get("/api/strategy/goals/" + goal).with(as(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void reorderRenumbersTheOpenGoalsAndPublishesOnlyTheMovedOnes() throws Exception {
        UUID user = UUID.randomUUID();
        String a = id(create(user, "A"));
        String b = id(create(user, "B"));
        String c = id(create(user, "C"));

        mockMvc.perform(put("/api/strategy/goals/priority-order").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalIds\": [\"" + c + "\", \"" + a + "\", \"" + b + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.contains("C", "A", "B")))
                .andExpect(jsonPath("$[*].priority").value(org.hamcrest.Matchers.contains(1, 2, 3)));
        assertEquals(6, outboxPayloads(user, EVENT).size(), "3 created + 3 moved (each changed place)");

        mockMvc.perform(put("/api/strategy/goals/priority-order").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalIds\": [\"" + c + "\", \"" + a + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STRATEGY_GOAL_ORDER_MISMATCH"))
                .andExpect(jsonPath("$.openGoals").value(3));
    }

    private ResultActions create(UUID user, String title) throws Exception {
        String body = """
                {"title": "%s", "domain": "CAREER", "whyItMatters": "Ownership",
                 "windowStart": "2026-09-01", "windowEnd": "2027-06-30"}
                """.formatted(title);
        return mockMvc.perform(post("/api/strategy/goals").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(UUID user, String goalId, int version, String fields) throws Exception {
        String body = fields.replaceFirst("\\{", "{\"version\": " + version + ", ");
        return mockMvc.perform(patch("/api/strategy/goals/" + goalId).with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String id(ResultActions created) throws Exception {
        return jsonMapper.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asString();
    }
}

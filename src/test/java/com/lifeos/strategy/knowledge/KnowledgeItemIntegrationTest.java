package com.lifeos.strategy.knowledge;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

class KnowledgeItemIntegrationTest extends IntegrationTest {

    private static final String EVENT = "KnowledgeItemChanged";

    @Test
    void aNewItemStartsUnverifiedAndIsPublished() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);

        create(user, goal, "Target companies want K8s experience", "ASSUMPTION")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.external").value(true))
                .andExpect(jsonPath("$.howToVerify").value("Scan 20 job posts"))
                .andExpect(jsonPath("$.verifiedAt").isEmpty())
                .andExpect(jsonPath("$.version").value(0));

        List<JsonNode> events = outboxPayloads(user, EVENT);
        assertEquals(1, events.size());
        EventSchemas.assertValidPayload(EVENT, 1, events.getFirst());
    }

    @Test
    void settlingStampsVerifiedAtAndReopeningClearsIt() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);
        String item = id(create(user, goal, "Target companies want K8s experience", "ASSUMPTION"));

        update(user, goal, item, 0, "{\"status\": \"PARTIAL\", \"source\": \"12 of 20 posts\"}")
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.verifiedAt").isEmpty());
        update(user, goal, item, 1, "{\"status\": \"VERIFIED\"}")
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty())
                .andExpect(jsonPath("$.source").value("12 of 20 posts"));
        update(user, goal, item, 2, "{\"status\": \"UNVERIFIED\"}")
                .andExpect(jsonPath("$.verifiedAt").isEmpty())
                .andExpect(jsonPath("$.version").value(3));

        List<JsonNode> events = outboxPayloads(user, EVENT);
        assertEquals(4, events.size());
        events.forEach(event -> EventSchemas.assertValidPayload(EVENT, 1, event));
        assertFalse(events.get(2).get("verifiedAt").isNull(), "the VERIFIED event carries the time");
        assertTrue(events.get(3).get("verifiedAt").isNull());
        assertEquals(3, events.get(3).get("version").asInt());
    }

    @Test
    void listFiltersByKindAndStatus() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);
        create(user, goal, "Remote in-house roles exist", "FACT");
        String assumption = id(create(user, goal, "Companies want K8s", "ASSUMPTION"));
        create(user, goal, "Is system design asked first?", "UNKNOWN");
        update(user, goal, assumption, 0, "{\"status\": \"PARTIAL\"}");

        mockMvc.perform(get(items(goal)).with(as(user)))
                .andExpect(jsonPath("$[*].kind").value(contains("FACT", "ASSUMPTION", "UNKNOWN")));
        mockMvc.perform(get(items(goal)).param("kind", "ASSUMPTION").with(as(user)))
                .andExpect(jsonPath("$[*].statement").value(contains("Companies want K8s")));
        mockMvc.perform(get(items(goal)).param("status", "UNVERIFIED").with(as(user)))
                .andExpect(jsonPath("$[*].kind").value(contains("FACT", "UNKNOWN")));
    }

    @Test
    void aStaleVersionIsRejected() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);
        String item = id(create(user, goal, "Statement", "FACT"));
        update(user, goal, item, 0, "{\"statement\": \"Edited\"}").andExpect(status().isOk());

        update(user, goal, item, 0, "{\"statement\": \"Edited from an old copy\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.currentVersion").value(1));
    }

    @Test
    void itemsAreScopedToTheirGoalAndOwner() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);
        String otherGoal = createGoal(user);
        String item = id(create(user, goal, "Statement", "FACT"));

        mockMvc.perform(get(items(otherGoal) + "/" + item).with(as(user)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(items(goal) + "/" + item).with(as(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        create(UUID.randomUUID(), goal, "Not my goal", "FACT").andExpect(status().isNotFound());
    }

    @Test
    void aClosedGoalsKnowledgeMapIsReadOnly() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);
        String item = id(create(user, goal, "Statement", "FACT"));
        mockMvc.perform(patch("/api/strategy/goals/" + goal).with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0, \"status\": \"ACHIEVED\"}"))
                .andExpect(status().isOk());

        create(user, goal, "Too late", "FACT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_GOAL_CLOSED"));
        update(user, goal, item, 0, "{\"status\": \"VERIFIED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_GOAL_CLOSED"));
        mockMvc.perform(get(items(goal) + "/" + item).with(as(user))).andExpect(status().isOk());
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = createGoal(user);

        mockMvc.perform(post(items(goal)).with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statement\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post(items(goal)).with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statement\": \"x\", \"kind\": \"RUMOUR\"}"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions create(UUID user, String goalId, String statement, String kind) throws Exception {
        String body = """
                {"statement": "%s", "kind": "%s", "external": true, "howToVerify": "Scan 20 job posts"}
                """.formatted(statement, kind);
        return mockMvc.perform(post(items(goalId)).with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(UUID user, String goalId, String itemId, int version, String fields)
            throws Exception {
        String body = fields.replaceFirst("\\{", "{\"version\": " + version + ", ");
        return mockMvc.perform(patch(items(goalId) + "/" + itemId).with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createGoal(UUID user) throws Exception {
        String body = """
                {"title": "Move to an in-house backend role", "domain": "CAREER",
                 "windowStart": "2026-09-01", "windowEnd": "2027-06-30"}
                """;
        return id(mockMvc.perform(post("/api/strategy/goals").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    private String id(ResultActions created) throws Exception {
        return jsonMapper.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    private static String items(String goalId) {
        return "/api/strategy/goals/" + goalId + "/knowledge-items";
    }
}

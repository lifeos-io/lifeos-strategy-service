package com.lifeos.strategy.idea;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifeos.strategy.support.EventSchemas;
import com.lifeos.strategy.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class IdeaIntegrationTest extends IntegrationTest {

    @Test
    void anIdeaIsCapturedIntoTheInboxAndPublished() throws Exception {
        Setup setup = setup();

        capture(setup, "A Life OS CLI for quick evidence entry", setup.goal())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INBOX"))
                .andExpect(jsonPath("$.goalId").value(setup.goal()))
                .andExpect(jsonPath("$.evaluations").isEmpty())
                .andExpect(jsonPath("$.version").value(0));

        JsonNode event = outboxPayloads(setup.user(), "IdeaChanged").getFirst();
        EventSchemas.assertValidPayload("IdeaChanged", 1, event);
        assertEquals("INBOX", event.get("status").asString());
        assertTrue(event.get("latestEvaluation").isNull());
        assertEquals(0, event.get("evaluationCount").asInt());
    }

    @Test
    void anIdeaGoesFromEvaluationToActiveToAGoal() throws Exception {
        Setup setup = setup();
        String idea = id(capture(setup, "Write a talk on the outbox pattern", null));

        evaluate(setup, idea, "PARK").andExpect(status().isOk());
        evaluate(setup, idea, "PURSUE")
                .andExpect(jsonPath("$.evaluations[*].verdict").value(contains("PARK", "PURSUE")));
        activate(setup, idea)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.activatedAt").isNotEmpty());
        promote(setup, idea, setup.goal())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROMOTED"))
                .andExpect(jsonPath("$.promotedGoalId").value(setup.goal()))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        List<JsonNode> events = outboxPayloads(setup.user(), "IdeaChanged");
        assertEquals(List.of("INBOX", "INBOX", "INBOX", "ACTIVE", "PROMOTED"),
                events.stream().map(e -> e.get("status").asString()).toList());
        events.forEach(e -> EventSchemas.assertValidPayload("IdeaChanged", 1, e));
        JsonNode promoted = events.getLast();
        assertEquals("PURSUE", promoted.at("/latestEvaluation/verdict").asString());
        assertEquals(2, promoted.get("evaluationCount").asInt());
        assertEquals(setup.goal(), promoted.get("promotedGoalId").asString());
    }

    @Test
    void onlyAnIdeaWorthPursuingCanBecomeActive() throws Exception {
        Setup setup = setup();
        String idea = id(capture(setup, "Learn Go for the trigger service", null));

        activate(setup, idea)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_IDEA_NOT_PURSUED"));
        evaluate(setup, idea, "PURSUE");
        evaluate(setup, idea, "PARK");
        activate(setup, idea)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_IDEA_NOT_PURSUED"))
                .andExpect(jsonPath("$.verdict").value("PARK"));
    }

    @Test
    void atMostTwoIdeasAreActive() throws Exception {
        Setup setup = setup();
        String first = pursued(setup, "First");
        String second = pursued(setup, "Second");
        String third = pursued(setup, "Third");
        activate(setup, first).andExpect(status().isOk());
        activate(setup, second).andExpect(status().isOk());

        activate(setup, third)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_ACTIVE_IDEA_LIMIT"))
                .andExpect(jsonPath("$.activeCount").value(2))
                .andExpect(jsonPath("$.limit").value(2));

        // Freeing a slot lets the third one in; another user's ideas do not count.
        shelve(setup, first).andExpect(jsonPath("$.status").value("SHELVED"));
        activate(setup, third).andExpect(status().isOk());
        Setup other = setup();
        activate(other, pursued(other, "Someone else's")).andExpect(status().isOk());
    }

    @Test
    void concurrentActivationsCannotExceedTheLimit() throws Exception {
        Setup setup = setup();
        activate(setup, pursued(setup, "Already active")).andExpect(status().isOk());
        List<String> candidates = List.of(pursued(setup, "Candidate A"), pursued(setup, "Candidate B"));

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (String candidate : candidates) {
            calls.add(() -> {
                start.await();
                return activate(setup, candidate).andReturn().getResponse().getStatus();
            });
        }
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> futures = calls.stream().map(executor::submit).toList();
            start.countDown();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
        }

        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        mockMvc.perform(get("/api/strategy/ideas").param("status", "ACTIVE").with(as(setup.user())))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void statusesMoveOnlyAlongTheLifecycle() throws Exception {
        Setup setup = setup();
        String idea = id(capture(setup, "Shelve me", null));

        reopen(setup, idea)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_IDEA_STATUS"));
        promote(setup, idea, setup.goal())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_IDEA_STATUS"))
                .andExpect(jsonPath("$.currentStatus").value("INBOX"))
                .andExpect(jsonPath("$.target").value("PROMOTED"));
        shelve(setup, idea).andExpect(jsonPath("$.status").value("SHELVED"));
        evaluate(setup, idea, "PURSUE");
        activate(setup, idea)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_IDEA_STATUS"));
        reopen(setup, idea).andExpect(jsonPath("$.status").value("INBOX"));
        drop(setup, idea)
                .andExpect(jsonPath("$.status").value("DROPPED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
    }

    @Test
    void aClosedIdeaIsReadOnly() throws Exception {
        Setup setup = setup();
        String idea = id(capture(setup, "Drop me", null));
        drop(setup, idea).andExpect(jsonPath("$.version").value(1));

        update(setup, idea, 1, "{\"title\": \"Changed\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_IDEA_STATUS"))
                .andExpect(jsonPath("$.currentStatus").value("DROPPED"));
        evaluate(setup, idea, "PURSUE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_IDEA_STATUS"));
        activate(setup, idea)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_IDEA_STATUS"));
    }

    @Test
    void anIdeaIsEditedWithItsVersionAndOnlyRealChangesArePublished() throws Exception {
        Setup setup = setup();
        String idea = id(capture(setup, "Blog post", null));

        update(setup, idea, 0, "{\"title\": \"Blog post on the outbox\", \"goalId\": \"%s\"}"
                .formatted(setup.goal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Blog post on the outbox"))
                .andExpect(jsonPath("$.goalId").value(setup.goal()))
                .andExpect(jsonPath("$.version").value(1));
        update(setup, idea, 0, "{\"title\": \"Stale\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.currentVersion").value(1));
        update(setup, idea, 1, "{\"title\": \"Blog post on the outbox\"}")
                .andExpect(jsonPath("$.version").value(1));

        assertEquals(2, outboxPayloads(setup.user(), "IdeaChanged").size());
    }

    @Test
    void linksAndScoresAreValidated() throws Exception {
        Setup setup = setup();
        Setup other = setup();

        capture(setup, "Linked to someone else's goal", other.goal())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("goalId"));
        String idea = id(capture(setup, "Valid", null));
        mockMvc.perform(post("/api/strategy/ideas/" + idea + "/evaluations").with(as(setup.user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goalFit": 0, "evidencePotential": 6, "energy": 3, "weeklyMinutes": -1,
                                 "verdict": "PURSUE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("goalFit", "evidencePotential", "weeklyMinutes")));
        evaluate(setup, idea, "PURSUE");
        activate(setup, idea);
        promote(setup, idea, other.goal())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("goalId"));
    }

    @Test
    void listFiltersAndIdeasAreScopedToTheUser() throws Exception {
        Setup setup = setup();
        String linked = id(capture(setup, "Linked", setup.goal()));
        String active = pursued(setup, "Active");
        activate(setup, active);

        mockMvc.perform(get("/api/strategy/ideas").with(as(setup.user())))
                .andExpect(jsonPath("$[*].id").value(contains(active, linked)));
        mockMvc.perform(get("/api/strategy/ideas").param("status", "ACTIVE").with(as(setup.user())))
                .andExpect(jsonPath("$[*].id").value(contains(active)));
        mockMvc.perform(get("/api/strategy/ideas").param("goalId", setup.goal()).with(as(setup.user())))
                .andExpect(jsonPath("$[*].id").value(contains(linked)));
        mockMvc.perform(get("/api/strategy/ideas/" + linked).with(as(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        activate(setup(), active).andExpect(status().isNotFound());
    }

    /** A user with one open goal. */
    private Setup setup() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = id(mockMvc.perform(post("/api/strategy/goals").with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "In-house role", "domain": "CAREER",
                         "windowStart": "2026-09-01", "windowEnd": "2027-06-30"}
                        """)));
        return new Setup(user, goal);
    }

    private ResultActions capture(Setup setup, String title, String goalId) throws Exception {
        String body = """
                {"title": "%s", "note": "Came up while writing the outbox", "goalId": %s}
                """.formatted(title, goalId == null ? "null" : "\"" + goalId + "\"");
        return mockMvc.perform(post("/api/strategy/ideas").with(as(setup.user()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** A captured idea whose latest evaluation says PURSUE. */
    private String pursued(Setup setup, String title) throws Exception {
        String idea = id(capture(setup, title, null));
        evaluate(setup, idea, "PURSUE").andExpect(status().isOk());
        return idea;
    }

    private ResultActions evaluate(Setup setup, String ideaId, String verdict) throws Exception {
        String body = """
                {"goalFit": 4, "evidencePotential": 3, "energy": 5, "weeklyMinutes": 60, "verdict": "%s",
                 "note": "Fits the portfolio"}
                """.formatted(verdict);
        return mockMvc.perform(post("/api/strategy/ideas/" + ideaId + "/evaluations").with(as(setup.user()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(Setup setup, String ideaId, int version, String fields) throws Exception {
        String body = "{\"version\": " + version + ", " + fields.substring(1);
        return mockMvc.perform(patch("/api/strategy/ideas/" + ideaId).with(as(setup.user()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions activate(Setup setup, String ideaId) throws Exception {
        return transition(setup, ideaId, "activate");
    }

    private ResultActions shelve(Setup setup, String ideaId) throws Exception {
        return transition(setup, ideaId, "shelve");
    }

    private ResultActions reopen(Setup setup, String ideaId) throws Exception {
        return transition(setup, ideaId, "reopen");
    }

    private ResultActions drop(Setup setup, String ideaId) throws Exception {
        return transition(setup, ideaId, "drop");
    }

    private ResultActions promote(Setup setup, String ideaId, String goalId) throws Exception {
        return mockMvc.perform(post("/api/strategy/ideas/" + ideaId + "/promote").with(as(setup.user()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\": \"" + goalId + "\"}"));
    }

    private ResultActions transition(Setup setup, String ideaId, String verb) throws Exception {
        return mockMvc.perform(post("/api/strategy/ideas/" + ideaId + "/" + verb).with(as(setup.user())));
    }

    private String id(ResultActions created) throws Exception {
        return jsonMapper.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    private record Setup(UUID user, String goal) {
    }
}

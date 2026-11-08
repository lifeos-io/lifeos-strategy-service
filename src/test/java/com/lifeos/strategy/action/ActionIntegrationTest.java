package com.lifeos.strategy.action;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

class ActionIntegrationTest extends IntegrationTest {

    private static final String W46 = "2026-W46";
    private static final String EVENT = "ActionStatusChanged";

    @Test
    void actionsQueueUpInADraftWithoutEvents() throws Exception {
        Week week = week(540, 240);

        addAction(week, "Rate limit piece", 120, week.phaseKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.sortOrder").value(1))
                .andExpect(jsonPath("$.phaseKey").value(week.phaseKey()))
                .andExpect(jsonPath("$.goalIds[0]").value(week.goal()));
        addAction(week, "Blog post", 60, null).andExpect(jsonPath("$.sortOrder").value(2));

        mockMvc.perform(get(actions(W46)).with(as(week.user())))
                .andExpect(jsonPath("$[*].title").value(contains("Rate limit piece", "Blog post")));
        assertEquals(List.of(), outboxPayloads(week.user(), EVENT), "a draft is not binding yet");
    }

    @Test
    void linksMustPointIntoTheWeekAndTheCurrentStrategy() throws Exception {
        Week week = week(540, 240);
        String otherGoal = createGoal(week.user(), "Not in this week");

        String body = """
                {"title": "x", "kind": "TECHNICAL", "estimateMinutes": 30, "goalIds": ["%s"], "phaseKey": "%s"}
                """.formatted(otherGoal, UUID.randomUUID());
        String response = mockMvc.perform(post(actions(W46)).with(as(week.user()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        List<String> fields = jsonMapper.readTree(response).get("errors").valueStream()
                .map(error -> error.get("field").asString()).toList();
        assertEquals(List.of("goalIds", "phaseKey"), fields);
    }

    @Test
    void estimatesMayNotExceedTheWeek() throws Exception {
        Week week = week(300, 240);
        addAction(week, "Big one", 250, null).andExpect(status().isCreated());

        addAction(week, "Too much", 60, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STRATEGY_CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.plannedMinutes").value(310));
        // 245 still covers the goal's 240 minutes but not the 250 minutes of actions.
        mockMvc.perform(patch(plan(W46)).with(as(week.user())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0, \"capacityMinutes\": 245}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.plannedMinutes").value(250));
    }

    @Test
    void activationCountsTheWeeksActions() throws Exception {
        Week week = week(540, 240);
        addAction(week, "A", 120, null);
        addAction(week, "B", 60, null);

        activate(week);

        JsonNode activated = outboxPayloads(week.user(), "WeeklyPlanActivated").getFirst();
        EventSchemas.assertValidPayload("WeeklyPlanActivated", 1, activated);
        assertEquals(2, activated.get("plannedActionCount").asInt());
        assertEquals(180, activated.get("plannedActionMinutes").asInt());
    }

    @Test
    void statusChangesInTheActiveWeekArePublishedWithTheWeeksTotals() throws Exception {
        Week week = week(540, 240);
        String done = id(addAction(week, "Ship it", 120, null));
        String blog = id(addAction(week, "Blog post", 60, null));
        changeStatus(week, done, 0, "DONE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_PLAN_STATUS"));
        activate(week);

        changeStatus(week, done, 0, "DONE")
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        changeStatus(week, blog, 0, "DEFERRED").andExpect(jsonPath("$.deferredCount").value(1));
        changeStatus(week, blog, 1, "PLANNED").andExpect(jsonPath("$.deferredCount").value(1));
        changeStatus(week, blog, 2, "DEFERRED").andExpect(jsonPath("$.deferredCount").value(2));

        List<JsonNode> events = outboxPayloads(week.user(), EVENT);
        assertEquals(4, events.size());
        events.forEach(event -> EventSchemas.assertValidPayload(EVENT, 1, event));
        JsonNode last = events.getLast();
        assertEquals("PLANNED", last.get("previousStatus").asString());
        assertEquals(2, last.get("deferredCount").asInt());
        assertEquals(1, last.at("/week/done").asInt());
        assertEquals(1, last.at("/week/deferred").asInt());
        assertEquals(180, last.at("/week/plannedMinutes").asInt());
        assertEquals(120, last.at("/week/doneMinutes").asInt());
    }

    @Test
    void anActionAddedToTheActiveWeekIsPublishedAsNew() throws Exception {
        Week week = week(540, 240);
        activate(week);

        addAction(week, "Late addition", 30, null).andExpect(status().isCreated());

        JsonNode added = outboxPayloads(week.user(), EVENT).getFirst();
        EventSchemas.assertValidPayload(EVENT, 1, added);
        assertTrue(added.get("previousStatus").isNull());
        assertEquals("PLANNED", added.get("status").asString());
    }

    @Test
    void droppingFreesTheEstimateAndActiveWeeksKeepTheirActions() throws Exception {
        Week week = week(300, 240);
        String big = id(addAction(week, "Big", 250, null));
        activate(week);

        changeStatus(week, big, 0, "DROPPED").andExpect(status().isOk());
        addAction(week, "Fits now", 200, null).andExpect(status().isCreated());
        changeStatus(week, big, 1, "PLANNED")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STRATEGY_CAPACITY_EXCEEDED"));
        mockMvc.perform(delete(actions(W46) + "/" + big).with(as(week.user())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_PLAN_STATUS"));
    }

    @Test
    void draftActionsCanBeDeletedAndAGoalWithActionsStaysInTheSplit() throws Exception {
        Week week = week(540, 240);
        String action = id(addAction(week, "Temporary", 30, null));

        mockMvc.perform(patch(plan(W46)).with(as(week.user())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0, \"allocations\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("allocations"));

        mockMvc.perform(delete(actions(W46) + "/" + action).with(as(week.user())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(actions(W46)).with(as(week.user()))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void actionsAreScopedToTheirWeekAndOwnerAndVersioned() throws Exception {
        Week week = week(540, 240);
        String action = id(addAction(week, "Mine", 30, null));

        mockMvc.perform(get(actions(W46) + "/" + action).with(as(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(actions(W46) + "/" + action).with(as(week.user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 0, \"title\": \"Renamed\"}"))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(patch(actions(W46) + "/" + action).with(as(week.user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 0, \"title\": \"Stale\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    /** A user with one goal (with a strategy and one phase) and a draft plan for W46. */
    private Week week(int capacity, int allocated) throws Exception {
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
        String plan = """
                {"isoWeek": "%s", "capacityMinutes": %d, "allocations": [{"goalId": "%s", "allocatedMinutes": %d}]}
                """.formatted(W46, capacity, goal, allocated);
        mockMvc.perform(post("/api/strategy/weekly-plans").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(plan)).andExpect(status().isCreated());
        return new Week(user, goal, phaseKey);
    }

    private ResultActions addAction(Week week, String title, int minutes, String phaseKey) throws Exception {
        String body = """
                {"title": "%s", "why": "Portfolio evidence", "kind": "TECHNICAL", "estimateMinutes": %d,
                 "goalIds": ["%s"], "phaseKey": %s}
                """.formatted(title, minutes, week.goal(), phaseKey == null ? "null" : "\"" + phaseKey + "\"");
        return mockMvc.perform(post(actions(W46)).with(as(week.user()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions changeStatus(Week week, String actionId, int version, String status) throws Exception {
        return mockMvc.perform(put(actions(W46) + "/" + actionId + "/status").with(as(week.user()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": " + version + ", \"status\": \"" + status + "\"}"));
    }

    private void activate(Week week) throws Exception {
        mockMvc.perform(post(plan(W46) + "/activate").with(as(week.user()))).andExpect(status().isOk());
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

    private static String plan(String isoWeek) {
        return "/api/strategy/weekly-plans/" + isoWeek;
    }

    private static String actions(String isoWeek) {
        return plan(isoWeek) + "/actions";
    }

    private record Week(UUID user, String goal, String phaseKey) {
    }
}

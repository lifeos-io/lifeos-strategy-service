package com.lifeos.strategy.weeklyreview;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * Weeks in the past (2026-W40 is Sep 28 – Oct 4), so evidence on any day of the week is allowed whenever the
 * tests run.
 */
class WeeklyReviewIntegrationTest extends IntegrationTest {

    private static final String W40 = "2026-W40";
    private static final String W41 = "2026-W41";

    @Test
    void thePreviewCountsTheActiveWeekAsItIsNow() throws Exception {
        Week week = activeWeekWithWork(W40);

        mockMvc.perform(get(review(W40) + "/preview").with(as(week.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isoWeek").value(W40))
                .andExpect(jsonPath("$.weekStart").value("2026-09-28"))
                .andExpect(jsonPath("$.capacityMinutes").value(300))
                .andExpect(jsonPath("$.actions.planned").value(1))
                .andExpect(jsonPath("$.actions.done").value(2))
                .andExpect(jsonPath("$.actions.deferred").value(1))
                .andExpect(jsonPath("$.actions.dropped").value(1))
                .andExpect(jsonPath("$.actions.plannedMinutes").value(200))
                .andExpect(jsonPath("$.actions.doneMinutes").value(150))
                // The action serving both goals counts for both.
                .andExpect(jsonPath("$.goals[?(@.goalId == '%s')].allocatedMinutes".formatted(week.career()))
                        .value(contains(180)))
                .andExpect(jsonPath("$.goals[?(@.goalId == '%s')].doneMinutes".formatted(week.career()))
                        .value(contains(150)))
                .andExpect(jsonPath("$.goals[?(@.goalId == '%s')].doneMinutes".formatted(week.k3s()))
                        .value(contains(90)))
                // Monday and Sunday count; the Sunday before and the Monday after do not.
                .andExpect(jsonPath("$.evidenceCount").value(2));

        assertEquals(List.of(), outboxPayloads(week.user(), "WeeklyReviewCompleted"));
        mockMvc.perform(get(plan(W40)).with(as(week.user()))).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void writingTheReviewStoresTheNumbersClosesTheWeekAndIsPublished() throws Exception {
        Week week = activeWeekWithWork(W40);

        complete(week, W40, """
                {"energy": 3, "wentWell": "  Shipped the decisions module  ", "gotStuck": "Compose took longer",
                 "lesson": "Estimate infra work double", "nextWeekFocus": "Ideas module"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isoWeek").value(W40))
                .andExpect(jsonPath("$.actions.doneMinutes").value(150))
                .andExpect(jsonPath("$.evidenceCount").value(2))
                .andExpect(jsonPath("$.energy").value(3))
                .andExpect(jsonPath("$.wentWell").value("Shipped the decisions module"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mockMvc.perform(get(plan(W40)).with(as(week.user())))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
        mockMvc.perform(get(review(W40)).with(as(week.user())))
                .andExpect(jsonPath("$.lesson").value("Estimate infra work double"))
                .andExpect(jsonPath("$.goals.length()").value(2));

        List<JsonNode> events = outboxPayloads(week.user(), "WeeklyReviewCompleted");
        assertEquals(1, events.size());
        JsonNode event = events.getFirst();
        EventSchemas.assertValidPayload("WeeklyReviewCompleted", 1, event);
        assertEquals("2026-09-28", event.get("weekStart").asString());
        assertEquals(2, event.at("/actions/done").asInt());
        assertEquals(200, event.at("/actions/plannedMinutes").asInt());
        assertEquals("Ideas module", event.get("nextWeekFocus").asString());
    }

    @Test
    void theNumbersAreKeptAsTheyWereWhenTheReviewWasWritten() throws Exception {
        Week week = activeWeekWithWork(W40);
        complete(week, W40, "{\"energy\": 4}").andExpect(status().isCreated());

        // A closed week's actions cannot change, and later evidence does not alter the review.
        changeStatus(week, W40, week.planned(), 0, "DONE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_PLAN_STATUS"));
        recordEvidence(week, "2026-09-30");
        mockMvc.perform(get(review(W40)).with(as(week.user())))
                .andExpect(jsonPath("$.actions.done").value(2))
                .andExpect(jsonPath("$.evidenceCount").value(2))
                .andExpect(jsonPath("$.wentWell").isEmpty());
    }

    @Test
    void onlyTheActiveWeekCanBeReviewedAndOnlyOnce() throws Exception {
        Week week = activeWeekWithWork(W40);
        createPlan(week, W41);

        mockMvc.perform(get(review(W41) + "/preview").with(as(week.user())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_PLAN_STATUS"))
                .andExpect(jsonPath("$.currentStatus").value("DRAFT"));
        complete(week, W41, "{\"energy\": 3}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_PLAN_STATUS"));

        complete(week, W40, "{\"energy\": 3}").andExpect(status().isCreated());
        complete(week, W40, "{\"energy\": 5}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_PLAN_STATUS"))
                .andExpect(jsonPath("$.currentStatus").value("CLOSED"));
        assertEquals(1, outboxPayloads(week.user(), "WeeklyReviewCompleted").size());

        complete(week, "2026-W30", "{\"energy\": 3}").andExpect(status().isNotFound());
    }

    @Test
    void aWeekClosedWithoutAReviewHasNone() throws Exception {
        Week week = activeWeekWithWork(W40);
        mockMvc.perform(post(plan(W40) + "/close").with(as(week.user()))).andExpect(status().isOk());

        mockMvc.perform(get(review(W40)).with(as(week.user()))).andExpect(status().isNotFound());
        complete(week, W40, "{\"energy\": 3}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_PLAN_STATUS"));
    }

    @Test
    void theReflectionIsValidated() throws Exception {
        Week week = activeWeekWithWork(W40);

        complete(week, W40, "{\"wentWell\": \"No energy given\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("energy"));
        complete(week, W40, "{\"energy\": 6, \"nextWeekFocus\": \"%s\"}".formatted("x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
        mockMvc.perform(get(plan(W40)).with(as(week.user()))).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void reviewsAreListedNewestFirstAndScopedToTheUser() throws Exception {
        Week week = activeWeekWithWork(W40);
        complete(week, W40, "{\"energy\": 2}");
        createPlan(week, W41);
        mockMvc.perform(post(plan(W41) + "/activate").with(as(week.user()))).andExpect(status().isOk());
        complete(week, W41, "{\"energy\": 4}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/strategy/weekly-reviews").with(as(week.user())))
                .andExpect(jsonPath("$[*].isoWeek").value(contains(W41, W40)))
                .andExpect(jsonPath("$[*].energy").value(contains(4, 2)));

        UUID stranger = UUID.randomUUID();
        mockMvc.perform(get(review(W40)).with(as(stranger))).andExpect(status().isNotFound());
        mockMvc.perform(get(review(W40) + "/preview").with(as(stranger))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/strategy/weekly-reviews").with(as(stranger)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /**
     * A user with two goals and an active week: career 180 and k3s 120 of 300 minutes, five actions (done 60 for
     * career, done 90 for both, deferred 30 for k3s, dropped 45 for career, planned 20 for k3s), and evidence on
     * both edges of the week and just outside it.
     */
    private Week activeWeekWithWork(String isoWeek) throws Exception {
        UUID user = UUID.randomUUID();
        String career = createGoal(user, "In-house role");
        String k3s = createGoal(user, "Run Life OS on k3s");
        Week week = new Week(user, career, k3s, null);
        mockMvc.perform(post("/api/strategy/weekly-plans").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isoWeek": "%s", "capacityMinutes": 300,
                                 "allocations": [{"goalId": "%s", "allocatedMinutes": 180},
                                                 {"goalId": "%s", "allocatedMinutes": 120}]}
                                """.formatted(isoWeek, career, k3s)))
                .andExpect(status().isCreated());

        String done = createAction(week, isoWeek, "Decisions module", 60, career);
        String doneForBoth = createAction(week, isoWeek, "Release with compose files", 90, career, k3s);
        String deferred = createAction(week, isoWeek, "Compose for every service", 30, k3s);
        String dropped = createAction(week, isoWeek, "Blog post", 45, career);
        String planned = createAction(week, isoWeek, "Read the Strimzi docs", 20, k3s);
        mockMvc.perform(post(plan(isoWeek) + "/activate").with(as(user))).andExpect(status().isOk());

        changeStatus(week, isoWeek, done, 0, "DONE").andExpect(status().isOk());
        changeStatus(week, isoWeek, doneForBoth, 0, "DONE").andExpect(status().isOk());
        changeStatus(week, isoWeek, deferred, 0, "DEFERRED").andExpect(status().isOk());
        changeStatus(week, isoWeek, dropped, 0, "DROPPED").andExpect(status().isOk());

        for (String day : List.of("2026-09-27", "2026-09-28", "2026-10-04", "2026-10-05")) {
            recordEvidence(week, day);
        }
        return new Week(user, career, k3s, planned);
    }

    private String createGoal(UUID user, String title) throws Exception {
        return id(mockMvc.perform(post("/api/strategy/goals").with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "%s", "domain": "CAREER", "windowStart": "2026-09-01", "windowEnd": "2027-06-30"}
                        """.formatted(title))));
    }

    private void createPlan(Week week, String isoWeek) throws Exception {
        mockMvc.perform(post("/api/strategy/weekly-plans").with(as(week.user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isoWeek": "%s", "capacityMinutes": 300,
                                 "allocations": [{"goalId": "%s", "allocatedMinutes": 100}]}
                                """.formatted(isoWeek, week.career())))
                .andExpect(status().isCreated());
    }

    private String createAction(Week week, String isoWeek, String title, int minutes, String... goalIds)
            throws Exception {
        String goals = String.join("\", \"", goalIds);
        return id(mockMvc.perform(post(plan(isoWeek) + "/actions").with(as(week.user()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "%s", "kind": "TECHNICAL", "estimateMinutes": %d, "goalIds": ["%s"]}
                        """.formatted(title, minutes, goals))));
    }

    private ResultActions changeStatus(Week week, String isoWeek, String actionId, int version, String status)
            throws Exception {
        return mockMvc.perform(put(plan(isoWeek) + "/actions/" + actionId + "/status").with(as(week.user()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": %d, \"status\": \"%s\"}".formatted(version, status)));
    }

    private void recordEvidence(Week week, String occurredOn) throws Exception {
        mockMvc.perform(post("/api/strategy/evidence").with(as(week.user())).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Progress on %s", "level": "ACTIVITY", "strength": "PERSONAL_PROJECT",
                                 "occurredOn": "%s", "goalIds": ["%s"]}
                                """.formatted(occurredOn, occurredOn, week.career())))
                .andExpect(status().isCreated());
    }

    private ResultActions complete(Week week, String isoWeek, String body) throws Exception {
        return mockMvc.perform(post(review(isoWeek)).with(as(week.user()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String plan(String isoWeek) {
        return "/api/strategy/weekly-plans/" + isoWeek;
    }

    private static String review(String isoWeek) {
        return plan(isoWeek) + "/review";
    }

    private String id(ResultActions created) throws Exception {
        return jsonMapper.readTree(created.andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).get("id").asString();
    }

    /** {@code planned} is the action left PLANNED. */
    private record Week(UUID user, String career, String k3s, String planned) {
    }
}

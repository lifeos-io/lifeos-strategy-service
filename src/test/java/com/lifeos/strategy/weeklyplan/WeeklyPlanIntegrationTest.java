package com.lifeos.strategy.weeklyplan;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifeos.strategy.common.time.IsoWeeks;
import com.lifeos.strategy.support.EventSchemas;
import com.lifeos.strategy.support.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class WeeklyPlanIntegrationTest extends IntegrationTest {

    private static final String W46 = "2026-W46";

    @Test
    void aDraftTakesTheProfileCapacityAndPublishesNothing() throws Exception {
        UUID user = UUID.randomUUID();
        String career = createGoal(user, "Career");

        createPlan(user, W46, null, allocation(career, 240))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/strategy/weekly-plans/" + W46))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.weekStart").value("2026-11-09"))
                .andExpect(jsonPath("$.capacityMinutes").value(300))
                .andExpect(jsonPath("$.allocatedMinutes").value(240))
                .andExpect(jsonPath("$.allocations[0].strategyVersionId").isEmpty());

        assertEquals(List.of(), outboxPayloads(user, "WeeklyPlanActivated"));
    }

    @Test
    void goalsMayNotGetMoreThanTheWeekHas() throws Exception {
        UUID user = UUID.randomUUID();
        String career = createGoal(user, "Career");
        String company = createGoal(user, "Company");

        createPlan(user, W46, 540, allocation(career, 300) + "," + allocation(company, 300))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STRATEGY_CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.capacityMinutes").value(540))
                .andExpect(jsonPath("$.allocatedMinutes").value(600));

        createPlan(user, W46, 540, allocation(career, 240) + "," + allocation(company, 300))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0));
        // The version the client read is still current: the allocations were not rewritten on commit.
        update(user, W46, 0, "{\"capacityMinutes\": 500}")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void onlyTheUsersActiveGoalsGetMinutesEachOnce() throws Exception {
        UUID user = UUID.randomUUID();
        String active = createGoal(user, "Active");
        String paused = createGoal(user, "Paused");
        mockMvc.perform(patch("/api/strategy/goals/" + paused).with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": 0, \"status\": \"PAUSED\"}")).andExpect(status().isOk());
        String someoneElses = createGoal(UUID.randomUUID(), "Not mine");

        String response = createPlan(user, W46, 600, allocation(active, 60) + "," + allocation(paused, 60) + ","
                        + allocation(someoneElses, 60) + "," + allocation(active, 60))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        List<String> fields = jsonMapper.readTree(response).get("errors").valueStream()
                .map(error -> error.get("field").asString()).toList();
        assertEquals(List.of("allocations[1].goalId", "allocations[2].goalId", "allocations[3].goalId"), fields);
    }

    @Test
    void activationCommitsTheWeekWithEachGoalsCurrentStrategy() throws Exception {
        UUID user = UUID.randomUUID();
        String career = createGoal(user, "Career");
        String strategyVersionId = createStrategy(user, career);
        createPlan(user, W46, 540, allocation(career, 240)).andExpect(status().isCreated());

        mockMvc.perform(post(plan(W46) + "/activate").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.activatedAt").isNotEmpty())
                .andExpect(jsonPath("$.allocations[0].strategyVersionId").value(strategyVersionId));

        List<JsonNode> events = outboxPayloads(user, "WeeklyPlanActivated");
        assertEquals(1, events.size());
        EventSchemas.assertValidPayload("WeeklyPlanActivated", 1, events.getFirst());
        assertEquals(strategyVersionId, events.getFirst().at("/allocations/0/strategyVersionId").asString());
        assertEquals("2026-11-09", events.getFirst().get("weekStart").asString());
    }

    @Test
    void oneWeekIsActiveAtATime() throws Exception {
        UUID user = UUID.randomUUID();
        createPlan(user, "2026-W45", 300, "").andExpect(status().isCreated());
        createPlan(user, W46, 300, "").andExpect(status().isCreated());
        mockMvc.perform(post(plan("2026-W45") + "/activate").with(as(user))).andExpect(status().isOk());

        mockMvc.perform(post(plan(W46) + "/activate").with(as(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_PLAN_ALREADY_ACTIVE"))
                .andExpect(jsonPath("$.activeWeek").value("2026-W45"));

        mockMvc.perform(post(plan("2026-W45") + "/close").with(as(user)))
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(post(plan(W46) + "/activate").with(as(user))).andExpect(status().isOk());
        mockMvc.perform(get("/api/strategy/weekly-plans").param("status", "ACTIVE").with(as(user)))
                .andExpect(jsonPath("$[*].isoWeek").value(contains(W46)));
    }

    @Test
    void changingAnActiveWeeksCapacityIsPublished() throws Exception {
        UUID user = UUID.randomUUID();
        createPlan(user, W46, 540, "").andExpect(status().isCreated());
        update(user, W46, 0, "{\"capacityMinutes\": 600}").andExpect(status().isOk());
        assertEquals(List.of(), outboxPayloads(user, "CapacityChanged"), "a draft is not binding yet");

        mockMvc.perform(post(plan(W46) + "/activate").with(as(user))).andExpect(status().isOk());
        update(user, W46, 2, "{\"capacityMinutes\": 420, \"headline\": \"Sick for two days\"}")
                .andExpect(jsonPath("$.capacityMinutes").value(420));

        List<JsonNode> events = outboxPayloads(user, "CapacityChanged");
        assertEquals(1, events.size());
        EventSchemas.assertValidPayload("CapacityChanged", 1, events.getFirst());
        assertEquals(600, events.getFirst().get("previousCapacityMinutes").asInt());
        assertEquals(420, events.getFirst().get("capacityMinutes").asInt());
    }

    @Test
    void aClosedWeekIsReadOnlyAndAWeekHasOnePlan() throws Exception {
        UUID user = UUID.randomUUID();
        createPlan(user, W46, 300, "").andExpect(status().isCreated());
        createPlan(user, W46, 300, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_PLAN_EXISTS"));

        mockMvc.perform(post(plan(W46) + "/activate").with(as(user))).andExpect(status().isOk());
        mockMvc.perform(post(plan(W46) + "/close").with(as(user))).andExpect(status().isOk());
        update(user, W46, 2, "{\"headline\": \"Too late\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_PLAN_STATUS"))
                .andExpect(jsonPath("$.currentStatus").value("CLOSED"));
        mockMvc.perform(post(plan(W46) + "/activate").with(as(user)))
                .andExpect(jsonPath("$.code").value("STRATEGY_PLAN_STATUS"));
    }

    @Test
    void weeksAreValidatedAndScopedToTheUser() throws Exception {
        UUID user = UUID.randomUUID();
        // 2027 has 52 ISO weeks (2026 has 53: it starts on a Thursday).
        createPlan(user, "2027-W53", 300, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("isoWeek"));
        createPlan(user, "2026-46", 300, "").andExpect(status().isBadRequest());

        createPlan(user, W46, 300, "").andExpect(status().isCreated());
        mockMvc.perform(get(plan(W46)).with(as(UUID.randomUUID()))).andExpect(status().isNotFound());
        assertTrue(IsoWeeks.monday("2027-W53").isEmpty());
        assertEquals(LocalDate.of(2026, 12, 28), IsoWeeks.monday("2026-W53").orElseThrow());
        assertEquals("2026-W45", IsoWeeks.of(LocalDate.of(2026, 11, 8)));
    }

    private ResultActions createPlan(UUID user, String isoWeek, Integer capacity, String allocations)
            throws Exception {
        String body = """
                {"isoWeek": "%s", "capacityMinutes": %s, "allocations": [%s]}
                """.formatted(isoWeek, capacity, allocations);
        return mockMvc.perform(post("/api/strategy/weekly-plans").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(UUID user, String isoWeek, int version, String fields) throws Exception {
        String body = fields.replaceFirst("\\{", "{\"version\": " + version + ", ");
        return mockMvc.perform(patch(plan(isoWeek)).with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createGoal(UUID user, String title) throws Exception {
        String body = """
                {"title": "%s", "domain": "CAREER", "windowStart": "2026-09-01", "windowEnd": "2027-06-30"}
                """.formatted(title);
        return id(mockMvc.perform(post("/api/strategy/goals").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    private String createStrategy(UUID user, String goalId) throws Exception {
        String body = """
                {"summary": "Portfolio first", "effortAllocation": {"TECHNICAL": 100},
                 "phases": [{"title": "Foundation", "startsOn": "2026-09-01", "endsOn": "2026-12-31",
                             "status": "ACTIVE"}]}
                """;
        return id(mockMvc.perform(post("/api/strategy/goals/" + goalId + "/strategy-versions").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    private String id(ResultActions created) throws Exception {
        return jsonMapper.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    private static String allocation(String goalId, int minutes) {
        return "{\"goalId\": \"" + goalId + "\", \"allocatedMinutes\": " + minutes + "}";
    }

    private static String plan(String isoWeek) {
        return "/api/strategy/weekly-plans/" + isoWeek;
    }
}

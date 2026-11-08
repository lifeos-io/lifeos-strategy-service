package com.lifeos.strategy.decision;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

class DecisionIntegrationTest extends IntegrationTest {

    private static final String NEXT_MONTH = LocalDate.now().plusMonths(1).toString();

    @Test
    void aDraftLabelsItsAlternativesAndPublishesNothing() throws Exception {
        Setup setup = setup();

        draft(setup, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.alternatives[*].label").value(contains("A", "B", "C")))
                .andExpect(jsonPath("$.assumptionIds[0]").value(setup.assumption()))
                .andExpect(jsonPath("$.reopenTriggers[*].kind").value(contains("DATE", "EVENT")));

        assertEquals(List.of(), outboxPayloads(setup.user(), "DecisionRecorded"));
    }

    @Test
    void decidingCommitsToOneAlternativeAndIsPublished() throws Exception {
        Setup setup = setup();
        String decision = id(draft(setup, true));

        decide(setup, decision, 0, "B", NEXT_MONTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty())
                .andExpect(jsonPath("$.alternatives[1].chosen").value(true))
                .andExpect(jsonPath("$.alternatives[1].rejectionReason").isEmpty());

        List<JsonNode> events = outboxPayloads(setup.user(), "DecisionRecorded");
        assertEquals(1, events.size());
        EventSchemas.assertValidPayload("DecisionRecorded", 1, events.getFirst());
        assertEquals("B", events.getFirst().at("/alternatives/1/label").asString());
        assertEquals(setup.assumption(), events.getFirst().at("/assumptionIds/0").asString());
        assertEquals(NEXT_MONTH, events.getFirst().get("reviewOn").asString());
    }

    @Test
    void aDecisionNeedsReasonsForTheRejectedAlternativesAndAFutureReview() throws Exception {
        Setup setup = setup();
        String withoutReasons = id(draft(setup, false));

        String response = decide(setup, withoutReasons, 0, "B", LocalDate.now().toString())
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        List<String> fields = jsonMapper.readTree(response).get("errors").valueStream()
                .map(error -> error.get("field").asString()).toList();
        assertEquals(List.of("alternatives[0].rejectionReason", "alternatives[2].rejectionReason", "reviewOn"),
                fields);

        decide(setup, withoutReasons, 0, "Z", NEXT_MONTH)
                .andExpect(jsonPath("$.errors[0].field").value("chosenLabel"));
    }

    @Test
    void closingRecordsTheLookBackAndDecidedDecisionsAreHistory() throws Exception {
        Setup setup = setup();
        String decision = id(draft(setup, true));
        close(setup, decision, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_DECISION_STATUS"));
        decide(setup, decision, 0, "B", NEXT_MONTH).andExpect(status().isOk());

        mockMvc.perform(put("/api/strategy/decisions/" + decision).with(as(setup.user()))
                        .contentType(MediaType.APPLICATION_JSON).content(draftBody(setup, true, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_DECISION_STATUS"));
        close(setup, decision, 1)
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.wouldDecideAgain").value("GOOD_CALL_BAD_LUCK"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        JsonNode closed = outboxPayloads(setup.user(), "DecisionClosed").getFirst();
        EventSchemas.assertValidPayload("DecisionClosed", 1, closed);
        assertEquals("GOOD_CALL_BAD_LUCK", closed.get("wouldDecideAgain").asString());
    }

    @Test
    void aDraftCanBeRevisedWithItsVersion() throws Exception {
        Setup setup = setup();
        String decision = id(draft(setup, false));

        mockMvc.perform(put("/api/strategy/decisions/" + decision).with(as(setup.user()))
                        .contentType(MediaType.APPLICATION_JSON).content(draftBody(setup, true, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternatives[0].rejectionReason").value("Not in my hands"))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(put("/api/strategy/decisions/" + decision).with(as(setup.user()))
                        .contentType(MediaType.APPLICATION_JSON).content(draftBody(setup, true, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void linksAndTriggersAreValidated() throws Exception {
        Setup setup = setup();
        Setup other = setup();
        String body = """
                {"title": "Bad", "goalId": "%s", "assumptionIds": ["%s"],
                 "alternatives": [{"description": "A"}, {"description": "B"}],
                 "reopenTriggers": [{"kind": "DATE", "description": "No date"},
                                    {"kind": "DATE", "description": "Past", "firesOn": "%s"},
                                    {"kind": "EVENT", "description": "Dated event", "firesOn": "%s"}]}
                """.formatted(other.goal(), other.assumption(), LocalDate.now().minusDays(1), NEXT_MONTH);

        String response = mockMvc.perform(post("/api/strategy/decisions").with(as(setup.user()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        List<String> fields = jsonMapper.readTree(response).get("errors").valueStream()
                .map(error -> error.get("field").asString()).toList();
        assertEquals(List.of("goalId", "assumptionIds", "reopenTriggers[0].firesOn", "reopenTriggers[1].firesOn",
                "reopenTriggers[2].firesOn"), fields);
    }

    @Test
    void listFiltersAndDecisionsAreScopedToTheUser() throws Exception {
        Setup setup = setup();
        String open = id(draft(setup, true));
        decide(setup, open, 0, "B", NEXT_MONTH);
        String stillDraft = id(draft(setup, true));

        mockMvc.perform(get("/api/strategy/decisions").param("status", "OPEN").with(as(setup.user())))
                .andExpect(jsonPath("$[*].id").value(contains(open)));
        mockMvc.perform(get("/api/strategy/decisions").param("goalId", setup.goal()).with(as(setup.user())))
                .andExpect(jsonPath("$[*].id").value(contains(stillDraft, open)));
        mockMvc.perform(get("/api/strategy/decisions/" + open).with(as(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    /** A user with a goal and an assumption in its knowledge map. */
    private Setup setup() throws Exception {
        UUID user = UUID.randomUUID();
        String goal = id(mockMvc.perform(post("/api/strategy/goals").with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "In-house role", "domain": "CAREER",
                         "windowStart": "2026-09-01", "windowEnd": "2027-06-30"}
                        """)));
        String assumption = id(mockMvc.perform(post("/api/strategy/goals/" + goal + "/knowledge-items")
                .with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"statement\": \"Companies want K8s experience\", \"kind\": \"ASSUMPTION\"}")));
        return new Setup(user, goal, assumption);
    }

    private ResultActions draft(Setup setup, boolean withReasons) throws Exception {
        return mockMvc.perform(post("/api/strategy/decisions").with(as(setup.user()))
                .contentType(MediaType.APPLICATION_JSON).content(draftBody(setup, withReasons, null)));
    }

    /** The mock data's d1: where to get K8s experience; B (own k3s server) is the one to choose. */
    private String draftBody(Setup setup, boolean withReasons, Integer version) {
        return """
                {"version": %s, "title": "Where do I get K8s experience?", "goalId": "%s",
                 "context": "Team lead says K8s experience is missing",
                 "alternatives": [{"description": "Ask for a transfer to the K8s project", "rejectionReason": %s},
                                  {"description": "Run Life OS on my own k3s server"},
                                  {"description": "Study for the CKA", "rejectionReason": %s}],
                 "assumptionIds": ["%s"],
                 "reopenTriggers": [{"kind": "DATE", "description": "Review day", "firesOn": "%s"},
                                    {"kind": "EVENT", "description": "If I get assigned to the K8s project"}]}
                """.formatted(version, setup.goal(), withReasons ? "\"Not in my hands\"" : "null",
                withReasons ? "\"A certificate is not work evidence\"" : "null", setup.assumption(), NEXT_MONTH);
    }

    private ResultActions decide(Setup setup, String decisionId, int version, String chosen, String reviewOn)
            throws Exception {
        String body = """
                {"version": %d, "chosenLabel": "%s", "expectedOutcome": "K8s work I can put on my CV",
                 "reviewOn": "%s"}
                """.formatted(version, chosen, reviewOn);
        return mockMvc.perform(post("/api/strategy/decisions/" + decisionId + "/decide").with(as(setup.user()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions close(Setup setup, String decisionId, int version) throws Exception {
        String body = """
                {"version": %d, "outcomeNote": "Server ran, but no interview asked about it",
                 "wouldDecideAgain": "GOOD_CALL_BAD_LUCK"}
                """.formatted(version);
        return mockMvc.perform(post("/api/strategy/decisions/" + decisionId + "/close").with(as(setup.user()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String id(ResultActions created) throws Exception {
        return jsonMapper.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    private record Setup(UUID user, String goal, String assumption) {
    }
}

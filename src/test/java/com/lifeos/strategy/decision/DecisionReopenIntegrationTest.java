package com.lifeos.strategy.decision;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** Reopening decided decisions, and flagging open decisions whose assumptions were falsified. */
class DecisionReopenIntegrationTest extends IntegrationTest {

    private static final String NEXT_MONTH = LocalDate.now().plusMonths(1).toString();

    @Test
    void reopeningStartsALinkedDraftAndDecidingItSupersedesTheOpenOriginal() throws Exception {
        Setup setup = setup();
        String original = decided(setup);

        String draft = id(reopen(setup, original, "  The transfer is possible after all  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reopenedFromId").value(original))
                .andExpect(jsonPath("$.reopenReason").value("The transfer is possible after all"))
                .andExpect(jsonPath("$.title").value("Where do I get K8s experience?"))
                .andExpect(jsonPath("$.alternatives[*].label").value(contains("A", "B", "C")))
                .andExpect(jsonPath("$.alternatives[*].chosen").value(contains(false, false, false)))
                .andExpect(jsonPath("$.alternatives[0].rejectionReason").value("Not in my hands"))
                .andExpect(jsonPath("$.assumptionIds[0]").value(setup.assumption()))
                .andExpect(jsonPath("$.reopenTriggers.length()").value(2)));

        fetch(setup, original)
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reopenedAsId").value(draft));
        JsonNode reopened = outboxPayloads(setup.user(), "DecisionReopened").getFirst();
        EventSchemas.assertValidPayload("DecisionReopened", 1, reopened);
        assertEquals(original, reopened.get("reopenedFromId").asString());
        assertEquals(0, reopened.get("falsifiedAssumptionIds").size());

        decide(setup, draft, 0).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));

        fetch(setup, original)
                .andExpect(jsonPath("$.status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.supersededAt").isNotEmpty());
        JsonNode superseded = outboxPayloads(setup.user(), "DecisionSuperseded").getFirst();
        EventSchemas.assertValidPayload("DecisionSuperseded", 1, superseded);
        assertEquals(original, superseded.get("decisionId").asString());
        assertEquals(draft, superseded.get("supersededById").asString());
        assertEquals(2, outboxPayloads(setup.user(), "DecisionRecorded").size());
    }

    @Test
    void aClosedOriginalKeepsItsLookBack() throws Exception {
        Setup setup = setup();
        String original = decided(setup);
        mockMvc.perform(post("/api/strategy/decisions/" + original + "/close").with(as(setup.user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 1, \"outcomeNote\": \"No interview asked\", \"wouldDecideAgain\": \"NO\"}"))
                .andExpect(status().isOk());

        String draft = id(reopen(setup, original, "Would not decide the same way").andExpect(status().isCreated()));
        decide(setup, draft, 0).andExpect(status().isOk());

        fetch(setup, original)
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.wouldDecideAgain").value("NO"))
                .andExpect(jsonPath("$.reopenedAsId").value(draft));
        assertEquals(List.of(), outboxPayloads(setup.user(), "DecisionSuperseded"));
    }

    @Test
    void onlyADecidedDecisionIsReopenedAndOnlyOnce() throws Exception {
        Setup setup = setup();
        String draftOnly = id(draft(setup));
        reopen(setup, draftOnly, "Too early")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_DECISION_STATUS"))
                .andExpect(jsonPath("$.currentStatus").value("DRAFT"));

        String original = decided(setup);
        reopen(setup, original, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        String reopened = id(reopen(setup, original, "First time").andExpect(status().isCreated()));
        reopen(setup, original, "Second time")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_DECISION_ALREADY_REOPENED"))
                .andExpect(jsonPath("$.reopenedAsId").value(reopened));

        // The reopened one is a chain link of its own once decided.
        decide(setup, reopened, 0).andExpect(status().isOk());
        reopen(setup, reopened, "And again").andExpect(status().isCreated());

        mockMvc.perform(post("/api/strategy/decisions/" + original + "/reopen").with(as(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Not mine\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFalsifiedAssumptionFlagsTheOpenDecisionsThatRestOnIt() throws Exception {
        Setup setup = setup();
        String open = decided(setup);
        String stillDraft = id(draft(setup));
        String closed = decided(setup);
        mockMvc.perform(post("/api/strategy/decisions/" + closed + "/close").with(as(setup.user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 1, \"outcomeNote\": \"Done\", \"wouldDecideAgain\": \"YES\"}"))
                .andExpect(status().isOk());

        updateAssumption(setup, 0, "{\"status\": \"FALSIFIED\"}").andExpect(status().isOk());

        // One event, for the decision being lived with.
        List<JsonNode> events = outboxPayloads(setup.user(), "DecisionAssumptionFalsified");
        assertEquals(1, events.size());
        EventSchemas.assertValidPayload("DecisionAssumptionFalsified", 1, events.getFirst());
        assertEquals(open, events.getFirst().get("decisionId").asString());
        assertEquals("Companies want K8s experience", events.getFirst().get("statement").asString());
        fetch(setup, open).andExpect(jsonPath("$.falsifiedAssumptionIds[0]").value(setup.assumption()));
        fetch(setup, stillDraft).andExpect(jsonPath("$.falsifiedAssumptionIds[0]").value(setup.assumption()));
        needsReview(setup).andExpect(jsonPath("$[*].id").value(contains(open)));
        mockMvc.perform(get("/api/strategy/decisions").param("needsReview", "true").param("status", "CLOSED")
                        .with(as(setup.user())))
                .andExpect(jsonPath("$.length()").value(0));

        // Editing the falsified item does not announce it again; un-falsifying it lifts the flag.
        updateAssumption(setup, 1, "{\"statement\": \"Companies want K8s experience (checked)\"}")
                .andExpect(status().isOk());
        assertEquals(1, outboxPayloads(setup.user(), "DecisionAssumptionFalsified").size());
        updateAssumption(setup, 2, "{\"status\": \"UNVERIFIED\"}").andExpect(status().isOk());
        fetch(setup, open).andExpect(jsonPath("$.falsifiedAssumptionIds.length()").value(0));
        needsReview(setup).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void reopeningRecordsWhichAssumptionsWereFalsified() throws Exception {
        Setup setup = setup();
        String original = decided(setup);
        updateAssumption(setup, 0, "{\"status\": \"FALSIFIED\"}").andExpect(status().isOk());

        reopen(setup, original, "Its assumption was wrong")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.falsifiedAssumptionIds[0]").value(setup.assumption()));

        JsonNode reopened = outboxPayloads(setup.user(), "DecisionReopened").getFirst();
        EventSchemas.assertValidPayload("DecisionReopened", 1, reopened);
        assertEquals(setup.assumption(), reopened.at("/falsifiedAssumptionIds/0").asString());
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

    private ResultActions draft(Setup setup) throws Exception {
        return mockMvc.perform(post("/api/strategy/decisions").with(as(setup.user()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "Where do I get K8s experience?", "goalId": "%s",
                         "alternatives": [{"description": "Ask for a transfer", "rejectionReason": "Not in my hands"},
                                          {"description": "Run Life OS on my own k3s server"},
                                          {"description": "Study for the CKA", "rejectionReason": "Not work evidence"}],
                         "assumptionIds": ["%s"],
                         "reopenTriggers": [{"kind": "DATE", "description": "Review day", "firesOn": "%s"},
                                            {"kind": "EVENT", "description": "If I get the K8s project"}]}
                        """.formatted(setup.goal(), setup.assumption(), NEXT_MONTH)));
    }

    /** A drafted and decided (OPEN, version 1) decision choosing B. */
    private String decided(Setup setup) throws Exception {
        String decision = id(draft(setup));
        decide(setup, decision, 0).andExpect(status().isOk());
        return decision;
    }

    private ResultActions decide(Setup setup, String decisionId, int version) throws Exception {
        return mockMvc.perform(post("/api/strategy/decisions/" + decisionId + "/decide").with(as(setup.user()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d, "chosenLabel": "B", "expectedOutcome": "K8s work for my CV",
                         "reviewOn": "%s"}
                        """.formatted(version, NEXT_MONTH)));
    }

    private ResultActions reopen(Setup setup, String decisionId, String reason) throws Exception {
        return mockMvc.perform(post("/api/strategy/decisions/" + decisionId + "/reopen").with(as(setup.user()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.createObjectNode().put("reason", reason).toString()));
    }

    private ResultActions fetch(Setup setup, String decisionId) throws Exception {
        return mockMvc.perform(get("/api/strategy/decisions/" + decisionId).with(as(setup.user())));
    }

    private ResultActions needsReview(Setup setup) throws Exception {
        return mockMvc.perform(get("/api/strategy/decisions").param("needsReview", "true").with(as(setup.user())));
    }

    private ResultActions updateAssumption(Setup setup, int version, String fields) throws Exception {
        return mockMvc.perform(patch("/api/strategy/goals/" + setup.goal() + "/knowledge-items/" + setup.assumption())
                .with(as(setup.user())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": " + version + ", " + fields.substring(1)));
    }

    private String id(ResultActions created) throws Exception {
        return jsonMapper.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    private record Setup(UUID user, String goal, String assumption) {
    }
}

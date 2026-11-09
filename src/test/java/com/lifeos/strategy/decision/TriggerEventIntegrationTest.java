package com.lifeos.strategy.decision;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifeos.base.id.UuidV7;
import com.lifeos.strategy.decision.consumer.TriggerEventConsumer;
import com.lifeos.strategy.support.EventSchemas;
import com.lifeos.strategy.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Trigger's ReopenTriggerFired, recorded on the decision's trigger. */
class TriggerEventIntegrationTest extends IntegrationTest {

    private static final String NEXT_MONTH = LocalDate.now().plusMonths(1).toString();
    private static final String FIRED_AT = "2026-12-01T06:00:00Z";

    @Autowired
    private TriggerEventConsumer triggerEventConsumer;

    @Test
    void aFiredDateTriggerIsRecordedOnTheDecision() throws Exception {
        UUID user = UUID.randomUUID();
        String decision = decided(user);

        deliver(user, fired(decision, 0));

        fetch(user, decision)
                .andExpect(jsonPath("$.reopenTriggers[0].firedAt").value(FIRED_AT))
                .andExpect(jsonPath("$.reopenTriggers[1].firedAt").isEmpty())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void aTriggerFiresOnceAndOnlyWhileTheDecisionIsOpen() throws Exception {
        UUID user = UUID.randomUUID();
        String decision = decided(user);
        deliver(user, fired(decision, 0));

        // The same trigger again (a new event, e.g. after Trigger lost its state): unchanged.
        ObjectNode again = fired(decision, 0).put("dueAt", "2027-01-01T06:00:00Z");
        deliver(user, again);
        fetch(user, decision)
                .andExpect(jsonPath("$.reopenTriggers[0].firedAt").value(FIRED_AT))
                .andExpect(jsonPath("$.version").value(2));

        // A closed decision is history; an unknown decision or position is ignored.
        String closed = decided(user);
        mockMvc.perform(post("/api/strategy/decisions/" + closed + "/close").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 1, \"outcomeNote\": \"Done\", \"wouldDecideAgain\": \"YES\"}"))
                .andExpect(status().isOk());
        deliver(user, fired(closed, 0));
        deliver(user, fired(UUID.randomUUID().toString(), 0));
        deliver(user, fired(decision, 7));
        fetch(user, closed).andExpect(jsonPath("$.reopenTriggers[0].firedAt").isEmpty());
    }

    @Test
    void anotherUsersDecisionIsNotTouchedAndOtherTriggerEventsAreIgnored() throws Exception {
        UUID owner = UUID.randomUUID();
        String decision = decided(owner);

        deliver(UUID.randomUUID(), fired(decision, 0));
        triggerEventConsumer.on(record(owner, "DriftDetected", jsonMapper.createObjectNode().put("isoWeek", "2026-W46")));

        fetch(owner, decision).andExpect(jsonPath("$.reopenTriggers[0].firedAt").isEmpty());
    }

    private ObjectNode fired(String decisionId, int position) {
        ObjectNode payload = jsonMapper.createObjectNode()
                .put("decisionId", decisionId)
                .put("position", position)
                .put("description", "Review day")
                .put("firesOn", "2026-12-01")
                .put("dueAt", FIRED_AT);
        EventSchemas.assertValidTriggerPayload("ReopenTriggerFired", 1, payload);
        return payload;
    }

    private void deliver(UUID user, JsonNode payload) {
        triggerEventConsumer.on(record(user, "ReopenTriggerFired", payload));
    }

    private ConsumerRecord<String, String> record(UUID user, String eventType, JsonNode payload) {
        ObjectNode envelope = jsonMapper.createObjectNode()
                .put("eventId", UuidV7.next().toString())
                .put("eventType", eventType)
                .put("eventVersion", 1)
                .put("occurredAt", Instant.now().toString())
                .put("userId", user.toString());
        envelope.set("payload", payload);
        return new ConsumerRecord<>("lifeos.trigger.events.v1", 0, 0L, user.toString(),
                jsonMapper.writeValueAsString(envelope));
    }

    /** An OPEN decision (version 1) with a DATE trigger (position 0) and an EVENT trigger (position 1). */
    private String decided(UUID user) throws Exception {
        String decision = jsonMapper.readTree(mockMvc.perform(post("/api/strategy/decisions").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Where do I get K8s experience?",
                                 "alternatives": [{"description": "Transfer", "rejectionReason": "Not in my hands"},
                                                  {"description": "Own k3s server"}],
                                 "reopenTriggers": [{"kind": "DATE", "description": "Review day", "firesOn": "%s"},
                                                    {"kind": "EVENT", "description": "If I get the K8s project"}]}
                                """.formatted(NEXT_MONTH)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(post("/api/strategy/decisions/" + decision + "/decide").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "chosenLabel": "B", "expectedOutcome": "K8s on my CV",
                                 "reviewOn": "%s"}
                                """.formatted(NEXT_MONTH)))
                .andExpect(status().isOk());
        return decision;
    }

    private ResultActions fetch(UUID user, String decisionId) throws Exception {
        return mockMvc.perform(get("/api/strategy/decisions/" + decisionId).with(as(user)));
    }
}

package com.lifeos.strategy.feedback;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

class FeedbackIntegrationTest extends IntegrationTest {

    private static final String EVENT = "FeedbackRecorded";

    @Test
    void recordedFeedbackIsPublished() throws Exception {
        UUID user = UUID.randomUUID();

        record(user, "MANAGER", "K8s experience looks thin", "2026-10-14")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceKind").value("MANAGER"))
                .andExpect(jsonPath("$.sourceLabel").value("Team lead"))
                .andExpect(jsonPath("$.reliability").value("HIGH"));

        List<JsonNode> events = outboxPayloads(user, EVENT);
        assertEquals(1, events.size());
        EventSchemas.assertValidPayload(EVENT, 1, events.getFirst());
        assertEquals("K8s experience looks thin", events.getFirst().get("content").asString());
    }

    @Test
    void feedbackIsListedNewestFirstAndScopedToTheUser() throws Exception {
        UUID user = UUID.randomUUID();
        record(user, "MANAGER", "Older", "2026-10-14");
        String newer = jsonMapper.readTree(record(user, "CODE_REVIEW", "Newer", "2026-11-04")
                .andReturn().getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(get("/api/strategy/feedback").with(as(user)))
                .andExpect(jsonPath("$[*].content").value(contains("Newer", "Older")));
        mockMvc.perform(get("/api/strategy/feedback/" + newer).with(as(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void feedbackFromTheFutureOrWithoutContentIsRejected() throws Exception {
        UUID user = UUID.randomUUID();

        record(user, "PEER", "Not yet said", LocalDate.now().plusDays(2).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("receivedOn"));
        record(user, "PEER", " ", "2026-11-01").andExpect(status().isBadRequest());
        assertEquals(List.of(), outboxPayloads(user, EVENT));
    }

    private ResultActions record(UUID user, String sourceKind, String content, String receivedOn) throws Exception {
        String body = """
                {"sourceKind": "%s", "sourceLabel": "Team lead", "content": "%s", "reliability": "HIGH",
                 "receivedOn": "%s"}
                """.formatted(sourceKind, content, receivedOn);
        return mockMvc.perform(post("/api/strategy/feedback").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}

package com.lifeos.strategy.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifeos.base.http.LifeOsHeaders;
import com.lifeos.strategy.support.EventSchemas;
import com.lifeos.strategy.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;

class ProfileIntegrationTest extends IntegrationTest {

    private static final String EVENT = "UserProfileUpdated";

    @Autowired
    KafkaContainer kafkaContainer;

    @Test
    void firstCallCreatesTheProfileWithDefaultsAndPublishesItOnce() throws Exception {
        UUID user = UUID.randomUUID();

        mockMvc.perform(get("/api/strategy/me").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.toString()))
                .andExpect(jsonPath("$.timezone").value("Europe/Istanbul"))
                .andExpect(jsonPath("$.locale").value("tr-TR"))
                .andExpect(jsonPath("$.defaultWeeklyMinutes").value(300))
                .andExpect(jsonPath("$.onboardingCompleted").value(false));
        mockMvc.perform(get("/api/strategy/me").with(as(user))).andExpect(status().isOk());

        List<JsonNode> events = outboxPayloads(user, EVENT);
        assertEquals(1, events.size(), "created once, the second call only reads");
        EventSchemas.assertValidPayload(EVENT, 1, events.getFirst());
    }

    @Test
    void patchChangesOnlyTheGivenFieldsAndPublishesRealChangesOnly() throws Exception {
        UUID user = UUID.randomUUID();
        String body = """
                {"displayName": "  Onurcan ", "timezone": "Europe/Berlin", "onboardingCompleted": true}
                """;

        mockMvc.perform(patch("/api/strategy/me").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Onurcan"))
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.locale").value("tr-TR"))
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
        // Same values again: nothing changes, nothing is published.
        mockMvc.perform(patch("/api/strategy/me").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        List<JsonNode> events = outboxPayloads(user, EVENT);
        assertEquals(2, events.size(), "one for the creation, one for the change");
        JsonNode updated = events.getLast();
        EventSchemas.assertValidPayload(EVENT, 1, updated);
        assertEquals("Onurcan", updated.get("displayName").asString());
        assertEquals("Europe/Berlin", updated.get("timezone").asString());
        assertFalse(updated.get("onboardingCompletedAt").isNull());
    }

    @Test
    void invalidValuesAreRejectedPerField() throws Exception {
        String body = """
                {"displayName": "   ", "timezone": "+03:00", "locale": "not a tag", "defaultWeeklyMinutes": 20000}
                """;

        String response = mockMvc.perform(patch("/api/strategy/me").with(as(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn().getResponse().getContentAsString();

        List<String> fields = jsonMapper.readTree(response).get("errors").valueStream()
                .map(error -> error.get("field").asString()).sorted().toList();
        assertEquals(List.of("defaultWeeklyMinutes", "displayName", "locale", "timezone"), fields);
    }

    @Test
    void withoutATokenTheProfileIsNotReachable() throws Exception {
        mockMvc.perform(get("/api/strategy/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void theEventReachesKafkaInTheContractEnvelope() throws Exception {
        UUID user = UUID.randomUUID();
        String correlationId = "profile-" + UUID.randomUUID();

        mockMvc.perform(get("/api/strategy/me").with(as(user)).header(LifeOsHeaders.CORRELATION_ID, correlationId))
                .andExpect(status().isOk());

        ConsumerRecord<String, String> record = awaitRecord(user.toString());
        JsonNode envelope = jsonMapper.readTree(record.value());
        EventSchemas.assertValidEnvelope(envelope);
        EventSchemas.assertValidPayload(EVENT, 1, envelope.get("payload"));
        assertEquals(EVENT, envelope.get("eventType").asString());
        assertEquals(user.toString(), envelope.get("userId").asString());
        assertEquals(correlationId, envelope.get("correlationId").asString());
    }

    /** The relay publishes within its poll interval; read the topic until the user's record shows up. */
    private ConsumerRecord<String, String> awaitRecord(String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("lifeos.strategy.events.v1"));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        assertTrue(false, "no Kafka record with key " + key);
        return null;
    }
}

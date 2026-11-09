package com.lifeos.strategy.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base of the service's integration tests: the whole application over MockMvc, against real PostgreSQL and
 * Kafka. Requests are authenticated with {@link #as(UUID)} (a JWT whose {@code sub} is the user ID), the way the
 * gateway forwards them.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0/realms/test",
        // Identity deletion retries quickly, so the retry and dead-letter path can be tested.
        "lifeos.strategy.identity.deletion-attempts=3",
        "lifeos.strategy.identity.retry-delay-ms=300"})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcClient jdbcClient;

    @Autowired
    protected JsonMapper jsonMapper;

    protected static RequestPostProcessor as(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    /** Payloads of the user's events of one type in the outbox, oldest first. */
    protected List<JsonNode> outboxPayloads(UUID userId, String eventType) {
        return jdbcClient.sql("""
                        SELECT payload::text FROM outbox_event
                        WHERE user_id = :userId AND event_type = :eventType ORDER BY seq
                        """)
                .param("userId", userId)
                .param("eventType", eventType)
                .query(String.class)
                .list()
                .stream()
                .map(jsonMapper::readTree)
                .toList();
    }

    /** Waits for something asynchronous (Kafka, the outbox relay) to happen. */
    protected static void awaitTrue(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("condition not met within " + timeout);
            }
            Thread.sleep(100);
        }
    }
}

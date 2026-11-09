package com.lifeos.strategy.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL and Kafka for integration tests (and a recording stand-in for Keycloak), the same images as lifeos-infra/local. As beans they live
 * as long as the cached Spring context, so all test classes with the same setup share one pair.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:3.9.1");
    }

    /** No Keycloak in the tests: identity deletions are recorded (KeycloakIdentityAccounts has its own test). */
    @Bean
    @Primary
    RecordingIdentityAccounts recordingIdentityAccounts() {
        return new RecordingIdentityAccounts();
    }
}

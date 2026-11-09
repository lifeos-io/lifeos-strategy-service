package com.lifeos.strategy.account.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifeos.strategy.account.client.IdentityAccounts.IdentityDeletion;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;

/**
 * The real HTTP exchange against a fake Keycloak (the JDK's HTTP server): the client-credentials token request,
 * the bearer token on the admin call, the URL, and how the answers are read.
 */
class KeycloakIdentityAccountsTest {

    private static final UUID EXISTING = UUID.randomUUID();
    private static final UUID MISSING = UUID.randomUUID();
    private static final UUID BROKEN = UUID.randomUUID();

    private final List<String> tokenRequests = new CopyOnWriteArrayList<>();
    private final List<String> deleteRequests = new CopyOnWriteArrayList<>();
    private HttpServer keycloak;
    private KeycloakIdentityAccounts identityAccounts;

    @BeforeEach
    void startFakeKeycloak() throws IOException {
        keycloak = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keycloak.createContext("/realms/lifeos/protocol/openid-connect/token", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            tokenRequests.add(exchange.getRequestHeaders().getFirst("Authorization") + " " + body);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            respond(exchange, 200, """
                    {"access_token": "admin-token", "token_type": "Bearer", "expires_in": 300}
                    """);
        });
        keycloak.createContext("/admin/realms/lifeos/users/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            deleteRequests.add(exchange.getRequestMethod() + " " + path + " "
                    + exchange.getRequestHeaders().getFirst("Authorization"));
            UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
            respond(exchange, id.equals(EXISTING) ? 204 : id.equals(MISSING) ? 404 : 500, "");
        });
        keycloak.start();
        String baseUrl = "http://127.0.0.1:" + keycloak.getAddress().getPort();

        ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak-admin")
                .clientId("lifeos-strategy")
                .clientSecret("s3cret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(baseUrl + "/realms/lifeos/protocol/openid-connect/token")
                .build();
        InMemoryClientRegistrationRepository registrations = new InMemoryClientRegistrationRepository(registration);
        identityAccounts = new KeycloakIdentityAccounts(RestClient.builder(), registrations,
                new InMemoryOAuth2AuthorizedClientService(registrations), baseUrl, "lifeos", "keycloak-admin");
    }

    @AfterEach
    void stopFakeKeycloak() {
        keycloak.stop(0);
    }

    @Test
    void deletesTheUserWithAServiceAccountToken() {
        assertEquals(IdentityDeletion.DELETED, identityAccounts.delete(EXISTING));

        assertEquals(List.of("DELETE /admin/realms/lifeos/users/" + EXISTING + " Bearer admin-token"), deleteRequests);
        assertEquals(1, tokenRequests.size());
        String tokenRequest = tokenRequests.getFirst();
        assertTrue(tokenRequest.startsWith("Basic "), tokenRequest);
        assertTrue(tokenRequest.contains("grant_type=client_credentials"), tokenRequest);
    }

    @Test
    void anAccountThatIsAlreadyGoneCountsAsDeleted() {
        assertEquals(IdentityDeletion.ALREADY_GONE, identityAccounts.delete(MISSING));
    }

    @Test
    void anyOtherAnswerFailsSoTheCallerRetries() {
        assertThrows(IllegalStateException.class, () -> identityAccounts.delete(BROKEN));
    }

    @Test
    void theTokenIsReusedWhileValid() {
        identityAccounts.delete(EXISTING);
        identityAccounts.delete(MISSING);

        assertEquals(1, tokenRequests.size());
        assertEquals(2, deleteRequests.size());
        assertTrue(deleteRequests.stream().allMatch(request -> request.endsWith("Bearer admin-token")));
    }

    @Test
    void anUnreachableKeycloakFails() {
        keycloak.stop(0);
        assertThrows(RuntimeException.class, () -> identityAccounts.delete(EXISTING));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}

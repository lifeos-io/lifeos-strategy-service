package com.lifeos.strategy.account.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Keycloak's admin REST API ({@code DELETE /admin/realms/{realm}/users/{id}}), called with a client-credentials
 * token of the {@code keycloak-admin} registration (a service account with only {@code manage-users}). Strategy
 * holds no admin password; the token is fetched and cached by Spring Security.
 */
@Component
public class KeycloakIdentityAccounts implements IdentityAccounts {

    /** Client credentials have no user: one fixed principal for the cached token. */
    private static final Authentication SERVICE = new AnonymousAuthenticationToken("lifeos-strategy",
            "lifeos-strategy", AuthorityUtils.createAuthorityList("ROLE_SERVICE"));

    private final RestClient restClient;
    private final String realm;

    public KeycloakIdentityAccounts(RestClient.Builder restClientBuilder,
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService oAuth2AuthorizedClientService,
            @Value("${lifeos.strategy.identity.admin-base-url}") String adminBaseUrl,
            @Value("${lifeos.strategy.identity.realm}") String realm,
            @Value("${lifeos.strategy.identity.client-registration-id}") String clientRegistrationId) {
        OAuth2ClientHttpRequestInterceptor tokens = new OAuth2ClientHttpRequestInterceptor(
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository,
                        oAuth2AuthorizedClientService));
        tokens.setClientRegistrationIdResolver(request -> clientRegistrationId);
        tokens.setPrincipalResolver(request -> SERVICE);
        // Bounded waits: a slow Keycloak fails the attempt, and the consumer's retry topic tries again later.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = restClientBuilder.clone()
                .baseUrl(adminBaseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(tokens)
                .build();
        this.realm = realm;
    }

    @Override
    public IdentityDeletion delete(UUID userId) {
        boolean gone = restClient.delete()
                .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .exchange((request, response) -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        return false;
                    }
                    if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                        return true;
                    }
                    throw new IllegalStateException("Keycloak refused to delete user " + userId + ": HTTP "
                            + response.getStatusCode().value());
                });
        return gone ? IdentityDeletion.ALREADY_GONE : IdentityDeletion.DELETED;
    }
}

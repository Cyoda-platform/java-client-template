package com.java_template.common.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.java_template.common.config.Config.*;

@Service
public class Authentication {

    private static final Logger logger = LoggerFactory.getLogger(Authentication.class);

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final ConcurrentMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    private static final String CACHE_KEY = "cyoda";

    public Authentication() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("cyoda")
                .tokenUri(CYODA_API_URL + "/oauth/token")
                .clientId(CYODA_CLIENT_ID)
                .clientSecret(CYODA_CLIENT_SECRET)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("default")
                .build();

        var registrationRepo = new InMemoryClientRegistrationRepository(registration);
        var clientService = new InMemoryOAuth2AuthorizedClientService(registrationRepo);
        this.authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrationRepo, clientService
        );
    }

    /**
     * Returns a valid access token, reusing it if still fresh.
     */
    public String getAccessToken() {
        CachedToken token = tokenCache.compute(CACHE_KEY, (key, existing) -> {
            if (existing != null && existing.isValid()) {
                return existing;
            }

            logger.info("Fetching new OAuth2 access token");
            OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId("cyoda")
                    .principal("cyoda-client")
                    .build();

            OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
            if (client == null || client.getAccessToken() == null) {
                throw new IllegalStateException("Failed to obtain access token");
            }

            OAuth2AccessToken accessToken = client.getAccessToken();
            logger.info("New token fetched, expires at: {}", accessToken.getExpiresAt());
            return new CachedToken(accessToken.getTokenValue(), accessToken.getExpiresAt());
        });

        return token.getToken();
    }

    /**
     * Clears cached token so next call re-authenticates.
     */
    public void invalidateTokens() {
        tokenCache.remove(CACHE_KEY);
        logger.info("Manually invalidated cached token");
    }

    /**
     * Simple container for access token with expiry.
     */
    private static class CachedToken {
        private final String token;
        private final Instant expiresAt;

        public CachedToken(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        public boolean isValid() {
            return expiresAt != null && Instant.now().isBefore(expiresAt.minusSeconds(60));
        }

        public String getToken() {
            return token;
        }
    }
}
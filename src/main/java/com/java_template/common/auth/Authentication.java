package com.java_template.common.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

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

        DefaultClientCredentialsTokenResponseClient tokenResponseClient = new DefaultClientCredentialsTokenResponseClient();

        RestTemplate restTemplate = new RestTemplate(
                new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory())
        );

        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            private final DefaultResponseErrorHandler defaultHandler = new DefaultResponseErrorHandler();

            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return defaultHandler.hasError(response);
            }

            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
                String body = new BufferedReader(new InputStreamReader(response.getBody()))
                        .lines().collect(Collectors.joining("\n"));
                logger.error("OAuth2 token fetch failed. Status code: {}, Body: {}",
                        response.getRawStatusCode(), body);
                defaultHandler.handleError(response);
            }
        });

        tokenResponseClient.setRestOperations(restTemplate);

        var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(c -> c.accessTokenResponseClient(tokenResponseClient))
                .build();

        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrationRepo, clientService
        );
        manager.setAuthorizedClientProvider(authorizedClientProvider);

        this.authorizedClientManager = manager;
    }

    public String getAccessToken() {
        CachedToken token = tokenCache.compute(CACHE_KEY, (key, existing) -> {
            if (existing != null && existing.isValid()) {
                return existing;
            }

            logger.info("Fetching new OAuth2 access token");
            OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId("cyoda")
                    .principal("cyoda-client")
                    .build();

            try {
                OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
                if (client == null || client.getAccessToken() == null) {
                    throw new IllegalStateException("Failed to obtain access token");
                }

                OAuth2AccessToken accessToken = client.getAccessToken();
                logger.info("New token fetched, expires at: {}", accessToken.getExpiresAt());
                return new CachedToken(accessToken.getTokenValue(), accessToken.getExpiresAt());
            } catch (Exception e) {
                logger.error("Unexpected error while fetching token", e);
                throw new RuntimeException("Unexpected error while fetching token", e);
            }
        });

        return token.getToken();
    }

    public void invalidateTokens() {
        tokenCache.remove(CACHE_KEY);
        logger.info("Manually invalidated cached token");
    }

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
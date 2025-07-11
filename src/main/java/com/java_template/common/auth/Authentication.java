package com.java_template.common.auth;

import com.java_template.common.config.Config;
import com.java_template.common.util.SslUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

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
        // Configure SSL context globally for OAuth2 client
        try {
            SSLContext sslContext = SslUtils.createSelectiveSSLContext();
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            // Set hostname verifier for trusted hosts
            if (Config.SSL_TRUST_ALL || !Config.getTrustedHosts().isEmpty()) {
                HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> {
                    if (Config.SSL_TRUST_ALL) {
                        logger.debug("Trusting hostname {} due to SSL_TRUST_ALL=true", hostname);
                        return true;
                    }
                    return SslUtils.shouldTrustHost(hostname);
                });
                logger.info("OAuth2 client configured with custom SSL settings for trusted hosts: {}",
                        Config.getTrustedHosts());
            }
        } catch (Exception e) {
            logger.error("Failed to configure SSL for OAuth2 client: {}", e.getMessage());
        }

        ClientRegistration registration = ClientRegistration.withRegistrationId("cyoda")
                .tokenUri(CYODA_API_URL + "/oauth/token")
                .clientId(CYODA_CLIENT_ID)
                .clientSecret(CYODA_CLIENT_SECRET)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("ROLE_M2M")
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
    public OAuth2AccessToken getAccessToken() {
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
            return new CachedToken(accessToken);
        });

        return token.oAuth2AccessToken;
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
        private record CachedToken(OAuth2AccessToken oAuth2AccessToken) {

        public boolean isValid() {
                Instant expiresAt = this.oAuth2AccessToken.getExpiresAt();
                return expiresAt != null && Instant.now().isBefore(expiresAt.minusSeconds(60));
            }

            public String getTokenValue() {
                return oAuth2AccessToken.getTokenValue();
            }
        }
}
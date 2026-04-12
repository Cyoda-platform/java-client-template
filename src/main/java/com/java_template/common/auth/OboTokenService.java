package com.java_template.common.auth;

// ABOUTME: Exchanges locally-signed subject tokens for Cyoda OBO access tokens (RFC 8693).
// Caches OBO tokens per user UUID in a Caffeine cache with per-entry expiry;
// entries expire 60 seconds before the token's stated expiry to avoid clock-skew races.

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.java_template.common.config.Config;
import com.java_template.common.util.SslUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class OboTokenService {

    private static final Logger logger = LoggerFactory.getLogger(OboTokenService.class);

    private static final String GRANT_TYPE =
            "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String SUBJECT_TOKEN_TYPE =
            "urn:ietf:params:oauth:token-type:jwt";
    private static final long EXPIRY_BUFFER_SECONDS = 60;
    private static final long FALLBACK_TTL_SECONDS = 3600;

    private final OboProperties oboProperties;
    private final SubjectTokenSigner subjectTokenSigner;
    private final Config config;
    private final RestClient restClient;

    private final Cache<String, OAuth2AccessToken> tokenCache = Caffeine.newBuilder()
            .expireAfter(new Expiry<String, OAuth2AccessToken>() {

                @Override
                public long expireAfterCreate(String key, OAuth2AccessToken token, long currentTime) {
                    return ttlNanos(token);
                }

                @Override
                public long expireAfterUpdate(String key, OAuth2AccessToken token,
                                              long currentTime, long currentDuration) {
                    return ttlNanos(token);
                }

                @Override
                public long expireAfterRead(String key, OAuth2AccessToken token,
                                            long currentTime, long currentDuration) {
                    return currentDuration;
                }

                private long ttlNanos(OAuth2AccessToken token) {
                    Instant expiresAt = token.getExpiresAt();
                    if (expiresAt == null) {
                        return TimeUnit.SECONDS.toNanos(FALLBACK_TTL_SECONDS);
                    }
                    long nanos = Duration.between(Instant.now(), expiresAt)
                            .minusSeconds(EXPIRY_BUFFER_SECONDS)
                            .toNanos();
                    return Math.max(0, nanos);
                }
            })
            .build();

    public OboTokenService(OboProperties oboProperties, SubjectTokenSigner subjectTokenSigner, Config config) {
        this.oboProperties = oboProperties;
        this.subjectTokenSigner = subjectTokenSigner;
        this.config = config;
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(SslUtils.createHttpClient(config)))
                .build();
    }

    public Optional<OAuth2AccessToken> getOboToken(String userId, List<String> userRoles) {
        if (!oboProperties.isEnabled()) {
            return Optional.empty();
        }
        OAuth2AccessToken token = tokenCache.get(userId, k -> exchange(userId, userRoles));
        return Optional.ofNullable(token);
    }

    public void invalidate(String userId) {
        tokenCache.invalidate(userId);
        logger.debug("Evicted OBO token cache for user {}", userId);
    }

    @SuppressWarnings("unchecked")
    private OAuth2AccessToken exchange(String userId, List<String> userRoles) {
        try {
            String subjectToken = subjectTokenSigner.sign(userId, userRoles);
            String credentials = Base64.getEncoder().encodeToString(
                    (config.getCyodaClientId() + ":" + config.getCyodaClientSecret()).getBytes()
            );
            String body = "grant_type=" + GRANT_TYPE
                    + "&subject_token=" + subjectToken
                    + "&subject_token_type=" + SUBJECT_TOKEN_TYPE;

            Map<String, Object> response = restClient.post()
                    .uri(config.getCyodaApiUrl() + "/oauth/token")
                    .header("Authorization", "Basic " + credentials)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("access_token")) {
                logger.warn("OBO exchange for user {} returned no access_token", userId);
                return null;
            }

            String tokenValue = (String) response.get("access_token");
            Number expiresIn = (Number) response.get("expires_in");
            String issuedTokenType = (String) response.get("issued_token_type");
            if (issuedTokenType != null) {
                logger.debug("OBO exchange issued_token_type: {}", issuedTokenType);
            }
            Instant expiresAt = expiresIn != null
                    ? Instant.now().plusSeconds(expiresIn.longValue())
                    : Instant.now().plusSeconds(FALLBACK_TTL_SECONDS);

            logger.debug("OBO token obtained for user {}, expires at {}", userId, expiresAt);
            return new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER, tokenValue, Instant.now(), expiresAt);

        } catch (OboTokenException e) {
            throw e;
        } catch (HttpClientErrorException.Forbidden e) {
            String detail = e.getResponseBodyAsString();
            throw new OboTokenException(
                    "OBO token exchange rejected: tenant boundary violation for user " + userId
                    + ". The M2M client and user belong to different tenants. "
                    + "Check APP_CONFIG_CYODA_CLIENT_ID configuration. Detail: " + detail);
        } catch (HttpClientErrorException.Unauthorized e) {
            String detail = e.getResponseBodyAsString();
            logger.error("OBO subject token rejected by Cyoda for user {} — "
                    + "the signing key may have expired or been invalidated. Detail: {}",
                    userId, detail);
            throw new OboTokenException(
                    "OBO subject token validation failed for user " + userId
                    + ". The signing key may need rotation. Detail: " + detail);
        } catch (Exception e) {
            logger.error("OBO token exchange failed for user {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }
}

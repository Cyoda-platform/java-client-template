package com.cyoda.app.oauth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * Service for generating and validating JWT access tokens.
 * Uses HMAC-SHA256 (HS256) for token signing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    @Value("${app.oauth.token-secret:}")
    private String tokenSecret;

    @Value("${app.oauth.token-expiration-seconds:3600}")
    private long tokenExpirationSeconds;

    /**
     * Generate a JWT access token for the given client.
     *
     * @param clientId the client identifier
     * @param scopes the set of scopes granted to the client
     * @return the JWT token string
     */
    public String generateToken(String clientId, Set<String> scopes) {
        if (tokenSecret == null || tokenSecret.isBlank()) {
            throw new IllegalStateException("Token secret not configured. Set CYODA_TOKEN_SECRET environment variable.");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(tokenExpirationSeconds);

        SecretKey key = Keys.hmacShaKeyFor(tokenSecret.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject(clientId)
                .claim("client_id", clientId)
                .claim("scopes", scopes)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        log.debug("Generated token for clientId: {}", clientId);
        return token;
    }

    /**
     * Validate a JWT token and extract claims.
     *
     * @param token the JWT token string
     * @return Claims if token is valid
     * @throws io.jsonwebtoken.JwtException if token is invalid or expired
     */
    public Claims validateToken(String token) {
        if (tokenSecret == null || tokenSecret.isBlank()) {
            throw new IllegalStateException("Token secret not configured.");
        }

        SecretKey key = Keys.hmacShaKeyFor(tokenSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extract client ID from a JWT token.
     *
     * @param token the JWT token string
     * @return the client ID
     */
    public String getClientIdFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.getSubject();
    }

    /**
     * Get the configured token expiration time in seconds.
     *
     * @return expiration time in seconds
     */
    public long getTokenExpirationSeconds() {
        return tokenExpirationSeconds;
    }
}


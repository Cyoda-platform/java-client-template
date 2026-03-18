package com.java_template.application.auth;

import org.springframework.stereotype.Component;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple JWT token provider for TMS prototype
 * Uses Base64 encoding for simplicity (not production-grade)
 */
@Component
public class JwtTokenProvider {
    private static final String SECRET = "tms-secret-key-for-prototype";
    private final Map<String, TokenInfo> tokenStore = new HashMap<>();

    /**
     * Generate a token for the given username
     */
    public String generateToken(String username) {
        String tokenId = UUID.randomUUID().toString();
        String payload = username + ":" + System.currentTimeMillis();
        String token = Base64.getEncoder().encodeToString(payload.getBytes());
        
        tokenStore.put(token, new TokenInfo(username, System.currentTimeMillis() + 3600000)); // 1 hour
        return token;
    }

    /**
     * Validate token and extract username
     */
    public String validateAndGetUsername(String token) {
        TokenInfo info = tokenStore.get(token);
        if (info == null || info.expiresAt < System.currentTimeMillis()) {
            return null;
        }
        return info.username;
    }

    /**
     * Revoke token
     */
    public void revokeToken(String token) {
        tokenStore.remove(token);
    }

    private static class TokenInfo {
        String username;
        long expiresAt;

        TokenInfo(String username, long expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }
}


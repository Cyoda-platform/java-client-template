package com.java_template.application.auth;

import org.springframework.stereotype.Component;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple JWT-like token provider using Base64 encoding
 * Hardcoded secret key and 24-hour expiration
 */
@Component
public class JwtTokenProvider {
    private static final String SECRET = "java-template-secret-key-12345";
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours
    private final Map<String, TokenData> tokenStore = new HashMap<>();

    /**
     * Generate a token for the given username and role
     */
    public String generateToken(String username, String role) {
        long issuedAt = System.currentTimeMillis();
        long expiresAt = issuedAt + EXPIRATION_TIME;

        String payload = username + "|" + role + "|" + issuedAt + "|" + expiresAt;
        String token = Base64.getEncoder().encodeToString(payload.getBytes());

        tokenStore.put(token, new TokenData(username, role, issuedAt, expiresAt));
        return token;
    }

    /**
     * Validate token
     */
    public boolean validateToken(String token) {
        TokenData data = tokenStore.get(token);
        if (data == null) {
            return false;
        }
        return data.expiresAt > System.currentTimeMillis();
    }

    /**
     * Get username from token
     */
    public String getUsernameFromToken(String token) {
        TokenData data = tokenStore.get(token);
        if (data == null || data.expiresAt <= System.currentTimeMillis()) {
            return null;
        }
        return data.username;
    }

    /**
     * Get role from token
     */
    public String getRoleFromToken(String token) {
        TokenData data = tokenStore.get(token);
        if (data == null || data.expiresAt <= System.currentTimeMillis()) {
            return null;
        }
        return data.role;
    }

    private static class TokenData {
        String username;
        String role;
        long issuedAt;
        long expiresAt;

        TokenData(String username, String role, long issuedAt, long expiresAt) {
            this.username = username;
            this.role = role;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }
    }
}


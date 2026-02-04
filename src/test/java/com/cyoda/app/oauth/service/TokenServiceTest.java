package com.cyoda.app.oauth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    private TokenService tokenService;
    private String testSecret;
    private String testClientId;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        testSecret = "my-super-secret-key-that-is-long-enough-for-hs256";
        testClientId = "test-client-001";

        ReflectionTestUtils.setField(tokenService, "tokenSecret", testSecret);
        ReflectionTestUtils.setField(tokenService, "tokenExpirationSeconds", 3600L);
    }

    @Test
    void testGenerateToken() {
        Set<String> scopes = Set.of("customer:read", "customer:write");

        String token = tokenService.generateToken(testClientId, scopes);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(token.contains("."));
    }

    @Test
    void testValidateToken() {
        Set<String> scopes = Set.of("customer:read");
        String token = tokenService.generateToken(testClientId, scopes);

        Claims claims = tokenService.validateToken(token);

        assertNotNull(claims);
        assertEquals(testClientId, claims.getSubject());
        assertEquals(testClientId, claims.get("client_id"));
    }

    @Test
    void testValidateTokenInvalid() {
        String invalidToken = "invalid.token.here";

        assertThrows(JwtException.class, () -> tokenService.validateToken(invalidToken));
    }

    @Test
    void testGetClientIdFromToken() {
        Set<String> scopes = Set.of("customer:read");
        String token = tokenService.generateToken(testClientId, scopes);

        String extractedClientId = tokenService.getClientIdFromToken(token);

        assertEquals(testClientId, extractedClientId);
    }

    @Test
    void testGetTokenExpirationSeconds() {
        long expirationSeconds = tokenService.getTokenExpirationSeconds();

        assertEquals(3600L, expirationSeconds);
    }

    @Test
    void testGenerateTokenWithoutSecret() {
        ReflectionTestUtils.setField(tokenService, "tokenSecret", "");

        assertThrows(IllegalStateException.class, () ->
                tokenService.generateToken(testClientId, Set.of()));
    }

    @Test
    void testValidateTokenWithoutSecret() {
        ReflectionTestUtils.setField(tokenService, "tokenSecret", "");

        assertThrows(IllegalStateException.class, () ->
                tokenService.validateToken("some.token.here"));
    }
}


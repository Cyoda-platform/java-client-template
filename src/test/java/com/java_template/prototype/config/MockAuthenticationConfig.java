package com.java_template.prototype.config;

import com.java_template.common.auth.Authentication;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Mock configuration for Authentication components in prototype mode.
 * Provides mock beans that simulate authentication behavior without requiring
 * actual OAuth2 connections or external authentication services.
 */
@TestConfiguration
@Profile("prototype")
public class MockAuthenticationConfig {

    /**
     * Creates a mock Authentication bean that returns a fake access token.
     * This allows prototype mode to work without requiring actual OAuth2 configuration.
     */
    @Bean
    @Primary
    public Authentication mockAuthentication() {
        Authentication mockAuth = Mockito.mock(Authentication.class);
        
        // Create a fake access token that expires in 1 hour
        OAuth2AccessToken fakeToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "mock-access-token-12345",
            Instant.now(),
            Instant.now().plus(1, ChronoUnit.HOURS)
        );
        
        // Configure the mock to return the fake token
        Mockito.when(mockAuth.getAccessToken()).thenReturn(fakeToken);
        
        // Mock the invalidateTokens method to do nothing
        Mockito.doNothing().when(mockAuth).invalidateTokens();
        
        return mockAuth;
    }
}

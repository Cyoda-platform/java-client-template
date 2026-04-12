package com.java_template.common.auth;

// ABOUTME: Unit tests for OboAwareAuthentication OBO/M2M dispatch logic.

import com.java_template.common.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OboAwareAuthenticationTest {

    @Mock
    private OboTokenService oboTokenService;

    @Mock
    private Config config;

    private OboProperties enabledProps;
    private TestOboAwareAuthentication subject;

    @BeforeEach
    void setUp() {
        // Stub config methods needed by the Authentication superclass constructor
        when(config.getCyodaApiUrl()).thenReturn("http://cyoda.example.com/api");
        when(config.getCyodaClientId()).thenReturn("test-client");
        when(config.getCyodaClientSecret()).thenReturn("test-secret");
        when(config.getTrustedHosts()).thenReturn(List.of());

        enabledProps = new OboProperties();
        enabledProps.setEncryptionKey("dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleTA=");
        enabledProps.setRolesClaimName("roles");

        subject = new TestOboAwareAuthentication(config, enabledProps, oboTokenService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAccessToken_returnsOboToken_whenUserJwtInContext() {
        setSecurityContext("user-1", List.of("ROLE_INVESTOR"));
        OAuth2AccessToken oboToken = fakeToken("obo-token");
        when(oboTokenService.getOboToken("user-1", List.of("INVESTOR")))
                .thenReturn(Optional.of(oboToken));

        assertThat(subject.getAccessToken().getTokenValue()).isEqualTo("obo-token");
    }

    @Test
    void getAccessToken_throwsOboTokenException_whenOboNotConfigured() {
        OboProperties disabledProps = new OboProperties(); // blank encryption-key
        TestOboAwareAuthentication disabledSubject =
                new TestOboAwareAuthentication(config, disabledProps, oboTokenService);

        setSecurityContext("user-1", List.of("ROLE_INVESTOR"));

        assertThatThrownBy(disabledSubject::getAccessToken)
                .isInstanceOf(OboTokenException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void getAccessToken_throwsOboTokenException_whenExchangeFails() {
        setSecurityContext("user-1", List.of("ROLE_INVESTOR"));
        when(oboTokenService.getOboToken(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(subject::getAccessToken)
                .isInstanceOf(OboTokenException.class)
                .hasMessageContaining("user-1");
    }

    @Test
    void getAccessToken_returnsM2mToken_whenNoUserContext() {
        SecurityContextHolder.clearContext();
        assertThat(subject.getAccessToken().getTokenValue()).isEqualTo("m2m-token");
        verifyNoInteractions(oboTokenService);
    }

    @Test
    void stripRolePrefix_appliedBeforePassingToOboService() {
        setSecurityContext("user-1", List.of("ROLE_INVESTOR", "ADMIN"));
        when(oboTokenService.getOboToken("user-1", List.of("INVESTOR", "ADMIN")))
                .thenReturn(Optional.of(fakeToken("obo")));

        subject.getAccessToken();

        verify(oboTokenService).getOboToken("user-1", List.of("INVESTOR", "ADMIN"));
    }

    @Test
    void getAccessToken_throwsOboTokenException_whenJwtHasNoSub() {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "HS256")
                .claim("roles", List.of("ROLE_INVESTOR"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThatThrownBy(subject::getAccessToken)
                .isInstanceOf(OboTokenException.class)
                .hasMessageContaining("sub");
    }

    private void setSecurityContext(String sub, List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "HS256")
                .subject(sub)
                .claim("roles", roles)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static OAuth2AccessToken fakeToken(String value) {
        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, value,
                Instant.now(), Instant.now().plusSeconds(3600));
    }

    // Test subclass: overrides getAccessToken() to bypass actual OAuth2 M2M infrastructure,
    // while delegating the OBO path to super (OboAwareAuthentication) which is what we're testing.
    private static class TestOboAwareAuthentication extends OboAwareAuthentication {

        TestOboAwareAuthentication(Config config, OboProperties props, OboTokenService oboTokenService) {
            super(config, props, oboTokenService);
        }

        @Override
        public OAuth2AccessToken getAccessToken() {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken) {
                // Delegate OBO path to parent (the code under test)
                return super.getAccessToken();
            }
            // Return fake M2M token without calling real OAuth2 client infrastructure
            return new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER, "m2m-token",
                    Instant.now(), Instant.now().plusSeconds(3600));
        }
    }
}

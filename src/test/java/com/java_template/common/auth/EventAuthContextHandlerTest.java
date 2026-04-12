package com.java_template.common.auth;

import io.cloudevents.v1.proto.CloudEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventAuthContextHandlerTest {

    @Mock
    private EventUserResolver userResolver;

    private OboProperties oboProperties;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        oboProperties = new OboProperties();
        oboProperties.setRolesClaimName("roles");
    }

    private EventAuthContextHandler handler(AuthContextMode mode) {
        EventAuthContextProperties props = new EventAuthContextProperties();
        props.setMode(mode);
        CloudEventAuthContextExtractor extractor = new CloudEventAuthContextExtractor();
        return new EventAuthContextHandler(props, extractor, userResolver, oboProperties);
    }

    // ── IGNORE mode ──────────────────────────────────────────────────────────

    @Test
    void establish_ignoreMode_clearsContextAndReturnsScope_withoutCallingResolver() {
        EventAuthContextHandler h = handler(AuthContextMode.IGNORE);
        CloudEvent event = userEvent("uuid-1", "{\"roles\":[\"USER\"]}");

        try (EventAuthContextScope scope = h.establish(event)) {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
        verifyNoInteractions(userResolver);
    }

    // ── REQUIRED mode — auth context absent ──────────────────────────────────

    @Test
    void establish_requiredMode_throwsMissing_whenNoAuthContext() {
        EventAuthContextHandler h = handler(AuthContextMode.REQUIRED);
        CloudEvent event = CloudEvent.newBuilder().build();

        assertThatThrownBy(() -> h.establish(event))
                .isInstanceOf(EventAuthContextMissingException.class)
                .hasMessageContaining("REQUIRED");
    }

    // ── OPTIONAL mode — auth context absent ──────────────────────────────────

    @Test
    void establish_optionalMode_clearsContext_whenNoAuthContext() {
        EventAuthContextHandler h = handler(AuthContextMode.OPTIONAL);
        CloudEvent event = CloudEvent.newBuilder().build();

        try (EventAuthContextScope scope = h.establish(event)) {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
        verifyNoInteractions(userResolver);
    }

    // ── authtype = user ───────────────────────────────────────────────────────

    @Test
    void establish_switchesContext_whenAuthTypeUser() {
        when(userResolver.resolve(any())).thenReturn(new EventUserIdentity("uuid-1", List.of("USER")));

        EventAuthContextHandler h = handler(AuthContextMode.REQUIRED);
        CloudEvent event = userEvent("uuid-1", "{\"roles\":[\"USER\"]}");

        try (EventAuthContextScope scope = h.establish(event)) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
            JwtAuthenticationToken jwt = (JwtAuthenticationToken) auth;
            assertThat(jwt.getToken().getSubject()).isEqualTo("uuid-1");
            assertThat(jwt.getToken().getClaimAsStringList("roles")).containsExactly("USER");
        }
    }

    @Test
    void establish_restoresContext_onScopeClose() {
        when(userResolver.resolve(any())).thenReturn(new EventUserIdentity("uuid-1", List.of()));

        EventAuthContextHandler h = handler(AuthContextMode.REQUIRED);
        CloudEvent event = userEvent("uuid-1", null);

        try (EventAuthContextScope ignored = h.establish(event)) {
            // inside scope — context is set
        }
        // after close — context restored to empty
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void establish_propagatesResolverException() {
        when(userResolver.resolve(any())).thenThrow(new EventUserResolutionException("not found"));

        EventAuthContextHandler h = handler(AuthContextMode.REQUIRED);
        CloudEvent event = userEvent("uuid-1", null);

        assertThatThrownBy(() -> h.establish(event))
                .isInstanceOf(EventUserResolutionException.class)
                .hasMessageContaining("not found");
    }

    // ── authtype != user ──────────────────────────────────────────────────────

    @Test
    void establish_clearsContext_whenAuthTypeServiceAccount() {
        EventAuthContextHandler h = handler(AuthContextMode.REQUIRED);
        CloudEvent event = nonUserEvent("service_account");

        try (EventAuthContextScope scope = h.establish(event)) {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
        verifyNoInteractions(userResolver);
    }

    @Test
    void establish_clearsContext_whenAuthTypeSystem() {
        EventAuthContextHandler h = handler(AuthContextMode.REQUIRED);
        CloudEvent event = nonUserEvent("system");

        try (EventAuthContextScope scope = h.establish(event)) {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    // ── synthetic JWT uses configured roles claim name ────────────────────────

    @Test
    void establish_usesConfiguredRolesClaimName_inSyntheticJwt() {
        oboProperties.setRolesClaimName("user_roles");
        when(userResolver.resolve(any())).thenReturn(new EventUserIdentity("uuid-1", List.of("ADMIN")));

        EventAuthContextHandler h = handler(AuthContextMode.REQUIRED);
        CloudEvent event = userEvent("uuid-1", null);

        try (EventAuthContextScope scope = h.establish(event)) {
            JwtAuthenticationToken jwt = (JwtAuthenticationToken)
                    SecurityContextHolder.getContext().getAuthentication();
            assertThat(jwt.getToken().getClaimAsStringList("user_roles")).containsExactly("ADMIN");
            assertThat(jwt.getToken().hasClaim("roles")).isFalse();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CloudEvent userEvent(String authId, String authClaims) {
        CloudEvent.Builder b = CloudEvent.newBuilder()
                .putAttributes("authtype", strAttr("user"))
                .putAttributes("authid",   strAttr(authId));
        if (authClaims != null) {
            b.putAttributes("authclaims", strAttr(authClaims));
        }
        return b.build();
    }

    private CloudEvent nonUserEvent(String authType) {
        return CloudEvent.newBuilder()
                .putAttributes("authtype", strAttr(authType))
                .build();
    }

    private CloudEvent.CloudEventAttributeValue strAttr(String value) {
        return CloudEvent.CloudEventAttributeValue.newBuilder().setCeString(value).build();
    }
}

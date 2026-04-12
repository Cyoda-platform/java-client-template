package com.java_template.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultEventUserResolverTest {

    private DefaultEventUserResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultEventUserResolver(new ObjectMapper());
    }

    @Test
    void resolve_returnsIdentity_whenAuthIdAndClaimsPresent() {
        CloudEventAuthContext ctx = new CloudEventAuthContext("user", "user-uuid-1", "{\"roles\":[\"USER\"]}");
        EventUserIdentity identity = resolver.resolve(ctx);
        assertThat(identity.userId()).isEqualTo("user-uuid-1");
        assertThat(identity.roles()).containsExactly("USER");
    }

    @Test
    void resolve_returnsEmptyRoles_whenAuthClaimsNull() {
        CloudEventAuthContext ctx = new CloudEventAuthContext("user", "user-uuid-1", null);
        EventUserIdentity identity = resolver.resolve(ctx);
        assertThat(identity.userId()).isEqualTo("user-uuid-1");
        assertThat(identity.roles()).isEmpty();
    }

    @Test
    void resolve_throws_whenAuthIdNull() {
        CloudEventAuthContext ctx = new CloudEventAuthContext("user", null, null);
        assertThatThrownBy(() -> resolver.resolve(ctx))
                .isInstanceOf(EventUserResolutionException.class)
                .hasMessageContaining("authid");
    }

    @Test
    void resolve_throws_whenAuthIdBlank() {
        CloudEventAuthContext ctx = new CloudEventAuthContext("user", "  ", null);
        assertThatThrownBy(() -> resolver.resolve(ctx))
                .isInstanceOf(EventUserResolutionException.class);
    }
}

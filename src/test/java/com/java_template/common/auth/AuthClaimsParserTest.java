package com.java_template.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthClaimsParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseRoles_returnsRoles_whenValidJson() {
        String json = "{\"legalEntityId\":\"org-1\",\"roles\":[\"USER\",\"ADMIN\"]}";
        assertThat(AuthClaimsParser.parseRoles(objectMapper, json)).containsExactly("USER", "ADMIN");
    }

    @Test
    void parseRoles_returnsEmpty_whenRolesFieldAbsent() {
        String json = "{\"legalEntityId\":\"org-1\"}";
        assertThat(AuthClaimsParser.parseRoles(objectMapper, json)).isEmpty();
    }

    @Test
    void parseRoles_returnsEmpty_whenInputNull() {
        assertThat(AuthClaimsParser.parseRoles(objectMapper, null)).isEmpty();
    }

    @Test
    void parseRoles_returnsEmpty_whenInputBlank() {
        assertThat(AuthClaimsParser.parseRoles(objectMapper, "   ")).isEmpty();
    }

    @Test
    void parseRoles_returnsEmpty_whenMalformedJson() {
        assertThat(AuthClaimsParser.parseRoles(objectMapper, "not-json")).isEmpty();
    }

    @Test
    void parseRoles_returnsEmpty_whenRolesIsNotList() {
        String json = "{\"roles\":\"single-string\"}";
        assertThat(AuthClaimsParser.parseRoles(objectMapper, json)).isEmpty();
    }

    @Test
    void parseRoles_convertsElementsToString() {
        String json = "{\"roles\":[\"USER\"]}";
        List<String> roles = AuthClaimsParser.parseRoles(objectMapper, json);
        assertThat(roles).containsExactly("USER");
    }

    @Test
    void authContextExtensionRolesKey_isRoles() {
        assertThat(AuthClaimsParser.AUTH_CONTEXT_EXTENSION_ROLES_KEY).isEqualTo("roles");
    }
}

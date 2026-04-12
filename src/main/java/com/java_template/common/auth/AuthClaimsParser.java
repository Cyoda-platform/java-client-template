package com.java_template.common.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Utility for parsing the role list from a Cyoda {@code authclaims} JSON string.
 * The key name is mandated by the Cyoda CloudEvents Auth Context Extension spec.
 */
public class AuthClaimsParser {

    /** Key mandated by the Cyoda CloudEvents Auth Context Extension specification. */
    public static final String AUTH_CONTEXT_EXTENSION_ROLES_KEY = "roles";

    private AuthClaimsParser() {}

    /**
     * Extracts the {@code roles} array from the Cyoda {@code authclaims} JSON attribute.
     * Returns an empty list if the input is blank, the field is absent, or parsing fails.
     */
    public static List<String> parseRoles(ObjectMapper objectMapper, String authClaimsJson) {
        if (authClaimsJson == null || authClaimsJson.isBlank()) return List.of();
        try {
            Map<String, Object> claims = objectMapper.readValue(
                    authClaimsJson, new TypeReference<>() {});
            Object roles = claims.get(AUTH_CONTEXT_EXTENSION_ROLES_KEY);
            if (roles instanceof List<?> list) {
                return list.stream().map(Object::toString).toList();
            }
        } catch (Exception ignored) { }
        return List.of();
    }
}

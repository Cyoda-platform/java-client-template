package com.java_template.common.auth;

import java.util.List;

/**
 * Resolved user identity returned by {@link EventUserResolver} for CloudEvent auth context processing.
 * Carries the user UUID and their roles for subsequent OBO token exchange.
 * Roles must not carry a {@code ROLE_} prefix — {@code OboAwareAuthentication} strips it, making stripping idempotent.
 */
public record EventUserIdentity(
        String userId,
        List<String> roles
) {}

package com.java_template.common.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for CloudEvent auth context handling.
 * Bound to {@code app.event.auth-context.*} in application.yml.
 */
@ConfigurationProperties(prefix = "app.event.auth-context")
public class EventAuthContextProperties {

    private AuthContextMode mode = AuthContextMode.REQUIRED;

    public AuthContextMode getMode() { return mode; }
    public void setMode(AuthContextMode mode) { this.mode = mode; }
}

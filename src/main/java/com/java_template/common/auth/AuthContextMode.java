package com.java_template.common.auth;

/**
 * Controls how the absence of auth context on a CloudEvent is handled during event processing.
 * REQUIRED fails the event; OPTIONAL warns and falls back to M2M; IGNORE always uses M2M.
 */
public enum AuthContextMode {
    REQUIRED,
    OPTIONAL,
    IGNORE
}

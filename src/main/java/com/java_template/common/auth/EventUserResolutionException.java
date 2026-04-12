package com.java_template.common.auth;

/**
 * Thrown when a CloudEvent auth context user cannot be resolved, does not exist,
 * or fails an application-level permission check. Triggers an error response to Cyoda.
 */
public class EventUserResolutionException extends RuntimeException {
    public EventUserResolutionException(String message) { super(message); }
    public EventUserResolutionException(String message, Throwable cause) { super(message, cause); }
}

package com.java_template.common.auth;

/**
 * Thrown when a CloudEvent carries no auth context and auth-context mode is REQUIRED.
 * Signals that the event cannot be processed and must return an error response.
 */
public class EventAuthContextMissingException extends RuntimeException {
    public EventAuthContextMissingException(String message) { super(message); }
}

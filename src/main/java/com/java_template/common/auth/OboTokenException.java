package com.java_template.common.auth;

// ABOUTME: Thrown when OBO token exchange fails for a user-triggered action.
// Signals that a Cyoda call cannot be attributed to the correct user identity.

public class OboTokenException extends RuntimeException {

    public OboTokenException(String message) {
        super(message);
    }

    public OboTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}

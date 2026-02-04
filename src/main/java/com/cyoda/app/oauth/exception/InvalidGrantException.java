package com.cyoda.app.oauth.exception;

/**
 * Exception thrown when the grant type is not supported or invalid.
 */
public class InvalidGrantException extends RuntimeException {

    public InvalidGrantException(String message) {
        super(message);
    }

    public InvalidGrantException(String message, Throwable cause) {
        super(message, cause);
    }
}


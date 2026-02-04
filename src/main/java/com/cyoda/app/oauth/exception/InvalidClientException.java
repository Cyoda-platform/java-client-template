package com.cyoda.app.oauth.exception;

/**
 * Exception thrown when client credentials are invalid or client is not found.
 */
public class InvalidClientException extends RuntimeException {

    public InvalidClientException(String message) {
        super(message);
    }

    public InvalidClientException(String message, Throwable cause) {
        super(message, cause);
    }
}


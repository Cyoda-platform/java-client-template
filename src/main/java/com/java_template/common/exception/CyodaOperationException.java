package com.java_template.common.exception;

/**
 * Thrown when a Cyoda platform operation returns a failure response.
 * Carries the error code, message, and retryable flag from the BaseEvent error payload.
 */
public class CyodaOperationException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;
    private final boolean retryable;

    public CyodaOperationException(String errorCode, String errorMessage, Boolean retryable) {
        super("Cyoda operation failed [" + errorCode + "]: " + errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.retryable = Boolean.TRUE.equals(retryable);
    }

    public CyodaOperationException(String errorCode, String errorMessage, Boolean retryable, Throwable cause) {
        super("Cyoda operation failed [" + errorCode + "]: " + errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.retryable = Boolean.TRUE.equals(retryable);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isRetryable() {
        return retryable;
    }
}

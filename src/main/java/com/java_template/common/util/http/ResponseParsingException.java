package com.java_template.common.util.http;

/**
 * ABOUTME: Exception thrown when response body parsing fails in strict/fail-fast mode.
 */
public class ResponseParsingException extends RuntimeException {
    private final int statusCode;
    private final String contentType;

    public ResponseParsingException(String message, int statusCode, String contentType) {
        super(message);
        this.statusCode = statusCode;
        this.contentType = contentType;
    }

    public ResponseParsingException(String message, Throwable cause, int statusCode, String contentType) {
        super(message, cause);
        this.statusCode = statusCode;
        this.contentType = contentType;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getContentType() {
        return contentType;
    }
}


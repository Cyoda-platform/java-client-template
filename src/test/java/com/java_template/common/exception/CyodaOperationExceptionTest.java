package com.java_template.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Tests for CyodaOperationException verifying message formatting,
 * field accessors, and default retryable behavior.
 */
class CyodaOperationExceptionTest {

    @Test
    void messageIncludesCodeAndDetail() {
        var ex = new CyodaOperationException("SERVER_ERROR", "something broke", true);

        assertEquals("SERVER_ERROR", ex.getErrorCode());
        assertEquals("something broke", ex.getErrorMessage());
        assertTrue(ex.isRetryable());
        assertTrue(ex.getMessage().contains("SERVER_ERROR"));
        assertTrue(ex.getMessage().contains("something broke"));
    }

    @Test
    void retryableDefaultsToFalseWhenNull() {
        var ex = new CyodaOperationException("CLIENT_ERROR", "bad request", null);

        assertFalse(ex.isRetryable());
    }

    @Test
    void isRuntimeException() {
        var ex = new CyodaOperationException("X", "msg", false);

        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void causeConstructorPreservesCause() {
        var cause = new IllegalStateException("root problem");
        var ex = new CyodaOperationException("PARSE_ERROR", "bad json", false, cause);

        assertSame(cause, ex.getCause());
        assertEquals("PARSE_ERROR", ex.getErrorCode());
    }
}

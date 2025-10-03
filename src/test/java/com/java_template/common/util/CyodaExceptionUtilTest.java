package com.java_template.common.util;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for CyodaExceptionUtil to verify error message extraction from Cyoda backend exceptions.
 */
class CyodaExceptionUtilTest {

    @Test
    void testExtractErrorMessage_withExternalErrorPattern() {
        // Simulate the actual error from Cyoda backend
        String statusMessage = "CANCELLED: Transaction c54997d0-a014-11f0-8025-ae468cd3ed18 was cancelled: " +
                "Sync process[61feaf8a-a00f-11f0-8025-ae468cd3ed18] for TreeNodeEntity[39b9c394-2d51-11b2-8cc0-7a8b886ecb2e] " +
                "failed: Fail with error code [PROCESSING_ERROR] with message 'External error: Invalid day count basis. " +
                "Must be ACT/365F, ACT/360, or ACT/365L. Got: 30/360'";

        StatusRuntimeException statusEx = new StatusRuntimeException(
                Status.CANCELLED.withDescription(statusMessage)
        );
        CompletionException exception = new CompletionException(statusEx);

        String result = CyodaExceptionUtil.extractErrorMessage(exception);

        assertEquals("Invalid day count basis. Must be ACT/365F, ACT/360, or ACT/365L. Got: 30/360", result);
    }

    @Test
    void testExtractErrorMessage_withoutExternalErrorPrefix() {
        String statusMessage = "Transaction failed: Fail with error code [VALIDATION_ERROR] " +
                "with message 'Field amount is required'";

        StatusRuntimeException statusEx = new StatusRuntimeException(
                Status.CANCELLED.withDescription(statusMessage)
        );
        CompletionException exception = new CompletionException(statusEx);

        String result = CyodaExceptionUtil.extractErrorMessage(exception);

        assertEquals("Field amount is required", result);
    }

    @Test
    void testExtractErrorMessage_withStatusRuntimeExceptionOnly() {
        String description = "Entity not found";
        StatusRuntimeException statusEx = new StatusRuntimeException(
                Status.NOT_FOUND.withDescription(description)
        );

        String result = CyodaExceptionUtil.extractErrorMessage(statusEx);

        assertEquals(description, result);
    }

    @Test
    void testExtractErrorMessage_withPlainException() {
        Exception exception = new IllegalArgumentException("Invalid input parameter");

        String result = CyodaExceptionUtil.extractErrorMessage(exception);

        assertEquals("Invalid input parameter", result);
    }

    @Test
    void testExtractErrorMessage_withNullException() {
        String result = CyodaExceptionUtil.extractErrorMessage(null);

        assertEquals("Unknown error", result);
    }

    @Test
    void testExtractErrorCode_withErrorCodePattern() {
        String statusMessage = "Transaction failed: Fail with error code [PROCESSING_ERROR] " +
                "with message 'External error: Something went wrong'";

        StatusRuntimeException statusEx = new StatusRuntimeException(
                Status.CANCELLED.withDescription(statusMessage)
        );
        CompletionException exception = new CompletionException(statusEx);

        String result = CyodaExceptionUtil.extractErrorCode(exception);

        assertEquals("PROCESSING_ERROR", result);
    }

    @Test
    void testExtractErrorCode_withoutErrorCodePattern() {
        Exception exception = new IllegalArgumentException("Invalid input");

        String result = CyodaExceptionUtil.extractErrorCode(exception);

        assertNull(result);
    }

    @Test
    void testExtractErrorCode_withNullException() {
        String result = CyodaExceptionUtil.extractErrorCode(null);

        assertNull(result);
    }

    @Test
    void testFormatErrorResponse_withErrorCode() {
        String statusMessage = "Transaction failed: Fail with error code [VALIDATION_ERROR] " +
                "with message 'External error: Invalid field value'";

        StatusRuntimeException statusEx = new StatusRuntimeException(
                Status.CANCELLED.withDescription(statusMessage)
        );
        CompletionException exception = new CompletionException(statusEx);

        String result = CyodaExceptionUtil.formatErrorResponse(exception);

        assertEquals("[VALIDATION_ERROR] Invalid field value", result);
    }

    @Test
    void testFormatErrorResponse_withoutErrorCode() {
        Exception exception = new IllegalArgumentException("Invalid input");

        String result = CyodaExceptionUtil.formatErrorResponse(exception);

        assertEquals("Invalid input", result);
    }

    @Test
    void testExtractErrorMessage_complexNestedMessage() {
        // Test with multiple nested quotes and special characters
        String statusMessage = "CANCELLED: Transaction abc was cancelled: " +
                "Sync process[xyz] failed: Fail with error code [PROCESSING_ERROR] " +
                "with message 'External error: Rate must be between 0.0 and 100.0. Got: 150.5'";

        StatusRuntimeException statusEx = new StatusRuntimeException(
                Status.CANCELLED.withDescription(statusMessage)
        );
        CompletionException exception = new CompletionException(statusEx);

        String result = CyodaExceptionUtil.extractErrorMessage(exception);

        assertEquals("Rate must be between 0.0 and 100.0. Got: 150.5", result);
    }
}


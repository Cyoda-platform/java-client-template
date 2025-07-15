package com.java_template.common.serializer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Test class for the common ErrorInfo record to verify its functionality
 * and validation behavior.
 */
class ErrorInfoTest {

    @Test
    @DisplayName("ErrorInfo should create successfully with valid code and message")
    void testValidErrorInfo() {
        // When
        ErrorInfo errorInfo = new ErrorInfo("TEST_ERROR", "Test error message");

        // Then
        assertEquals("TEST_ERROR", errorInfo.code());
        assertEquals("Test error message", errorInfo.message());
    }

    @Test
    @DisplayName("ErrorInfo should reject null code")
    void testNullCode() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new ErrorInfo(null, "Test message"));
        assertEquals("Error code cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("ErrorInfo should reject empty code")
    void testEmptyCode() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new ErrorInfo("", "Test message"));
        assertEquals("Error code cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("ErrorInfo should reject whitespace-only code")
    void testWhitespaceCode() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new ErrorInfo("   ", "Test message"));
        assertEquals("Error code cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("ErrorInfo should reject null message")
    void testNullMessage() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new ErrorInfo("TEST_ERROR", null));
        assertEquals("Error message cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("ErrorInfo.fromException should create ErrorInfo from exception")
    void testFromException() {
        // Given
        RuntimeException exception = new RuntimeException("Something went wrong");

        // When
        ErrorInfo errorInfo = ErrorInfo.fromException("RUNTIME_ERROR", exception);

        // Then
        assertEquals("RUNTIME_ERROR", errorInfo.code());
        assertEquals("Something went wrong", errorInfo.message());
    }

    @Test
    @DisplayName("ErrorInfo.fromException should handle exception with null message")
    void testFromExceptionWithNullMessage() {
        // Given
        RuntimeException exception = new RuntimeException((String) null);

        // When
        ErrorInfo errorInfo = ErrorInfo.fromException("RUNTIME_ERROR", exception);

        // Then
        assertEquals("RUNTIME_ERROR", errorInfo.code());
        assertEquals("RuntimeException", errorInfo.message());
    }

    @Test
    @DisplayName("ErrorInfo.processingError should create processing error")
    void testProcessingError() {
        // When
        ErrorInfo errorInfo = ErrorInfo.processingError("Processing failed");

        // Then
        assertEquals("PROCESSING_ERROR", errorInfo.code());
        assertEquals("Processing failed", errorInfo.message());
    }

    @Test
    @DisplayName("ErrorInfo.validationError should create validation error")
    void testValidationError() {
        // When
        ErrorInfo errorInfo = ErrorInfo.validationError("Validation failed");

        // Then
        assertEquals("VALIDATION_ERROR", errorInfo.code());
        assertEquals("Validation failed", errorInfo.message());
    }

    @Test
    @DisplayName("ErrorInfo.evaluationError should create evaluation error")
    void testEvaluationError() {
        // When
        ErrorInfo errorInfo = ErrorInfo.evaluationError("Evaluation failed");

        // Then
        assertEquals("EVALUATION_ERROR", errorInfo.code());
        assertEquals("Evaluation failed", errorInfo.message());
    }

    @Test
    @DisplayName("ErrorInfo should be equal when code and message are the same")
    void testEquality() {
        // Given
        ErrorInfo errorInfo1 = new ErrorInfo("TEST_ERROR", "Test message");
        ErrorInfo errorInfo2 = new ErrorInfo("TEST_ERROR", "Test message");

        // Then
        assertEquals(errorInfo1, errorInfo2);
        assertEquals(errorInfo1.hashCode(), errorInfo2.hashCode());
    }

    @Test
    @DisplayName("ErrorInfo should not be equal when code or message differ")
    void testInequality() {
        // Given
        ErrorInfo errorInfo1 = new ErrorInfo("TEST_ERROR", "Test message");
        ErrorInfo errorInfo2 = new ErrorInfo("OTHER_ERROR", "Test message");
        ErrorInfo errorInfo3 = new ErrorInfo("TEST_ERROR", "Other message");

        // Then
        assertNotEquals(errorInfo1, errorInfo2);
        assertNotEquals(errorInfo1, errorInfo3);
    }
}

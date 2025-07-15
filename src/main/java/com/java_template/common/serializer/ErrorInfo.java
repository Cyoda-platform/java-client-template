package com.java_template.common.serializer;

/**
 * ABOUTME: Common error information record used by both ProcessorSerializer and CriterionSerializer
 * for consistent error handling across the serialization framework.
 * 
 * Error information for response building.
 * Contains an error code and descriptive message for error responses.
 */
public record ErrorInfo(String code, String message) {
    
    /**
     * Creates an ErrorInfo with the given code and message.
     * 
     * @param code the error code (should not be null or empty)
     * @param message the error message (should not be null)
     * @throws IllegalArgumentException if code is null or empty, or if message is null
     */
    public ErrorInfo {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Error code cannot be null or empty");
        }
        if (message == null) {
            throw new IllegalArgumentException("Error message cannot be null");
        }
    }
    
    /**
     * Creates an ErrorInfo from an exception with a given code.
     * 
     * @param code the error code
     * @param exception the exception to extract the message from
     * @return ErrorInfo with the code and exception message
     */
    public static ErrorInfo fromException(String code, Throwable exception) {
        return new ErrorInfo(code, exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());
    }
    
    /**
     * Creates an ErrorInfo for processing errors.
     *
     * @param message the error message
     * @return ErrorInfo with "PROCESSING_ERROR" code
     */
    public static ErrorInfo processingError(String message) {
        return new ErrorInfo(StandardErrorCodes.PROCESSING_ERROR.getCode(), message);
    }

    /**
     * Creates an ErrorInfo for validation errors.
     *
     * @param message the error message
     * @return ErrorInfo with "VALIDATION_ERROR" code
     */
    public static ErrorInfo validationError(String message) {
        return new ErrorInfo(StandardErrorCodes.VALIDATION_ERROR.getCode(), message);
    }

    /**
     * Creates an ErrorInfo for evaluation errors.
     *
     * @param message the error message
     * @return ErrorInfo with "EVALUATION_ERROR" code
     */
    public static ErrorInfo evaluationError(String message) {
        return new ErrorInfo(StandardErrorCodes.EVALUATION_ERROR.getCode(), message);
    }
}

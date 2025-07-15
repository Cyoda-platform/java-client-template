package com.java_template.common.serializer;

/**
 * ABOUTME: Standard error codes enum that consolidates all hardcoded error types
 * used throughout the serialization framework and business logic.
 * 
 * Provides type-safe error codes and consistent error handling across the application.
 */
public enum StandardErrorCodes {
    
    // ========================================
    // CORE FRAMEWORK ERROR CODES
    // ========================================
    
    /**
     * General processing error - used when data processing fails.
     */
    PROCESSING_ERROR("PROCESSING_ERROR"),
    
    /**
     * Validation error - used when data validation fails.
     */
    VALIDATION_ERROR("VALIDATION_ERROR"),
    
    /**
     * Evaluation error - used when criteria evaluation fails.
     */
    EVALUATION_ERROR("EVALUATION_ERROR");
    
    private final String code;
    
    StandardErrorCodes(String code) {
        this.code = code;
    }
    
    /**
     * Gets the string representation of the error code.
     * 
     * @return the error code string
     */
    public String getCode() {
        return code;
    }
    
    /**
     * Returns the string representation of the error code.
     * This allows the enum to be used directly where strings are expected.
     * 
     * @return the error code string
     */
    @Override
    public String toString() {
        return code;
    }
}

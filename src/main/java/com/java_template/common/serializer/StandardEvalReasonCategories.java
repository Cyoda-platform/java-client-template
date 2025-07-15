package com.java_template.common.serializer;

/**
 * ABOUTME: Standard categories for evaluation failure reasons in the EvaluationChain.
 * Provides consistent categorization of different types of validation failures.
 * 
 * Enumeration of standard evaluation reason categories.
 * Used to categorize different types of validation failures consistently.
 */
public enum StandardEvalReasonCategories {
    
    /**
     * General validation failure category.
     */
    VALIDATION_FAILURE("VALIDATION_FAILURE"),
    
    /**
     * Business rule validation failure category.
     */
    BUSINESS_RULE_FAILURE("BUSINESS_RULE_FAILURE"),
    
    /**
     * Data quality validation failure category.
     */
    DATA_QUALITY_FAILURE("DATA_QUALITY_FAILURE"),
    
    /**
     * Structural validation failure category.
     */
    STRUCTURAL_FAILURE("STRUCTURAL_FAILURE");
    
    private final String code;
    
    StandardEvalReasonCategories(String code) {
        this.code = code;
    }
    
    /**
     * Gets the string code for this category.
     * 
     * @return the category code
     */
    public String getCode() {
        return code;
    }
    
    /**
     * Returns the string representation of this category.
     * 
     * @return the category code
     */
    @Override
    public String toString() {
        return code;
    }
}

package com.java_template.common.serializer;

/**
 * ABOUTME: Represents the reason for an evaluation failure in the EvaluationChain.
 * Provides context about why an evaluation failed, enabling detailed feedback.
 *
 * Evaluation reason for criterion operations.
 * Contains information about why an evaluation failed.
 */
public record EvaluationReason(
    String reason,
    String category
) {
    
    /**
     * Creates an EvaluationReason for a failed evaluation.
     *
     * @param reason the reason for failure
     * @return EvaluationReason for failure
     */
    public static EvaluationReason failure(String reason) {
        return new EvaluationReason(reason, StandardEvalReasonCategories.VALIDATION_FAILURE.getCode());
    }

    /**
     * Creates an EvaluationReason for a failed evaluation with category.
     *
     * @param reason the reason for failure
     * @param category the category of failure
     * @return EvaluationReason for failure
     */
    public static EvaluationReason failure(String reason, StandardEvalReasonCategories category) {
        return new EvaluationReason(reason, category.getCode());
    }

    /**
     * Creates an EvaluationReason for a structural validation failure.
     *
     * @param reason the reason for structural failure
     * @return EvaluationReason for structural failure
     */
    public static EvaluationReason structuralFailure(String reason) {
        return new EvaluationReason(reason, StandardEvalReasonCategories.STRUCTURAL_FAILURE.getCode());
    }


    /**
     * Creates an EvaluationReason for a business rule failure.
     *
     * @param reason the reason for business rule failure
     * @return EvaluationReason for business rule failure
     */
    public static EvaluationReason businessRuleFailure(String reason) {
        return new EvaluationReason(reason, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE.getCode());
    }

    /**
     * Creates an EvaluationReason for a data quality failure.
     *
     * @param reason the reason for data quality failure
     * @return EvaluationReason for data quality failure
     */
    public static EvaluationReason dataQualityFailure(String reason) {
        return new EvaluationReason(reason, StandardEvalReasonCategories.DATA_QUALITY_FAILURE.getCode());
    }
    
    /**
     * Formats the reason for display.
     *
     * @return formatted reason string
     */
    public String formatReason() {
        if (category != null && !category.isEmpty()) {
            return String.format("[%s] %s", category, reason);
        }
        return reason;
    }
}

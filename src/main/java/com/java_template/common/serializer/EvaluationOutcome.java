package com.java_template.common.serializer;

/**
 * ABOUTME: Sealed class hierarchy representing the outcome of an evaluation in the EvaluationChain.
 * Provides type-safe representation of success and failure outcomes with clear contracts.
 * 
 * Sealed class representing the outcome of an evaluation.
 * Can be either Success (no additional information) or Fail (with reason).
 */
public sealed class EvaluationOutcome permits EvaluationOutcome.Success, EvaluationOutcome.Fail {
    
    /**
     * Represents a successful evaluation outcome.
     * No additional information is needed for success cases.
     */
    public static final class Success extends EvaluationOutcome {
        private static final Success INSTANCE = new Success();
        
        private Success() {}
        
        /**
         * Gets the singleton instance of Success.
         * 
         * @return Success instance
         */
        public static Success getInstance() {
            return INSTANCE;
        }
        
        @Override
        public String toString() {
            return "EvaluationOutcome.Success";
        }
    }
    
    /**
     * Represents a failed evaluation outcome.
     * Contains the reason for the failure with optional category.
     */
    public static final class Fail extends EvaluationOutcome {
        private final String reason;
        private final String category;
        
        private Fail(String reason, String category) {
            this.reason = reason;
            this.category = category;
        }
        
        /**
         * Creates a Fail outcome with the given reason and category.
         * 
         * @param reason the reason for failure
         * @param category the category of failure
         * @return Fail outcome
         */
        public static Fail of(String reason, String category) {
            return new Fail(reason, category);
        }
        
        /**
         * Creates a Fail outcome with the given reason and default category.
         * 
         * @param reason the reason for failure
         * @return Fail outcome
         */
        public static Fail of(String reason) {
            return new Fail(reason, StandardEvalReasonCategories.VALIDATION_FAILURE.getCode());
        }
        
        /**
         * Creates a Fail outcome for a structural validation failure.
         * 
         * @param reason the reason for structural failure
         * @return Fail outcome
         */
        public static Fail structuralFailure(String reason) {
            return new Fail(reason, StandardEvalReasonCategories.STRUCTURAL_FAILURE.getCode());
        }
        
        /**
         * Creates a Fail outcome for a business rule failure.
         * 
         * @param reason the reason for business rule failure
         * @return Fail outcome
         */
        public static Fail businessRuleFailure(String reason) {
            return new Fail(reason, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE.getCode());
        }
        
        /**
         * Creates a Fail outcome for a data quality failure.
         * 
         * @param reason the reason for data quality failure
         * @return Fail outcome
         */
        public static Fail dataQualityFailure(String reason) {
            return new Fail(reason, StandardEvalReasonCategories.DATA_QUALITY_FAILURE.getCode());
        }
        
        /**
         * Creates a Fail outcome with the given reason and category enum.
         * 
         * @param reason the reason for failure
         * @param category the category of failure
         * @return Fail outcome
         */
        public static Fail of(String reason, StandardEvalReasonCategories category) {
            return new Fail(reason, category.getCode());
        }
        
        /**
         * Gets the reason for the failure.
         * 
         * @return the failure reason
         */
        public String getReason() {
            return reason;
        }
        
        /**
         * Gets the category of the failure.
         * 
         * @return the failure category
         */
        public String getCategory() {
            return category;
        }
        
        /**
         * Formats the failure reason for display.
         * 
         * @return formatted reason string
         */
        public String formatReason() {
            if (category != null && !category.isEmpty()) {
                return String.format("[%s] %s", category, reason);
            }
            return reason;
        }
        
        /**
         * Converts this Fail outcome to an EvaluationReason.
         * 
         * @return EvaluationReason equivalent
         */
        public EvaluationReason toEvaluationReason() {
            return new EvaluationReason(reason, category);
        }
        
        @Override
        public String toString() {
            return String.format("EvaluationOutcome.Fail{reason='%s', category='%s'}", reason, category);
        }
    }
    
    /**
     * Factory method to create a Success outcome.
     * 
     * @return Success outcome
     */
    public static Success success() {
        return Success.getInstance();
    }
    
    /**
     * Factory method to create a Fail outcome.
     * 
     * @param reason the reason for failure
     * @return Fail outcome
     */
    public static Fail fail(String reason) {
        return Fail.of(reason);
    }
    
    /**
     * Factory method to create a Fail outcome with category.
     *
     * @param reason the reason for failure
     * @param category the category of failure
     * @return Fail outcome
     */
    public static Fail fail(String reason, StandardEvalReasonCategories category) {
        return Fail.of(reason, category);
    }

    /**
     * Logical AND operation with another EvaluationOutcome.
     * Returns the first failure encountered, or success if both are successful.
     *
     * @param other the other EvaluationOutcome to AND with
     * @return Success if both are successful, otherwise the first failure
     */
    public EvaluationOutcome and(EvaluationOutcome other) {
        if (this instanceof Fail) {
            return this; // Return first failure
        }
        return other; // Return other (success or failure)
    }

    /**
     * Logical OR operation with another EvaluationOutcome.
     * Returns the first success encountered, or the last failure if both fail.
     *
     * @param other the other EvaluationOutcome to OR with
     * @return Success if either is successful, otherwise the last failure
     */
    public EvaluationOutcome or(EvaluationOutcome other) {
        if (this instanceof Success) {
            return this; // Return first success
        }
        return other; // Return other (success or failure)
    }

    /**
     * Convenience method to check if this outcome is a success.
     *
     * @return true if this is a Success outcome
     */
    public boolean isSuccess() {
        return this instanceof Success;
    }

    /**
     * Convenience method to check if this outcome is a failure.
     *
     * @return true if this is a Fail outcome
     */
    public boolean isFailure() {
        return this instanceof Fail;
    }

    /**
     * Chains multiple EvaluationOutcome suppliers with AND logic.
     * Returns the first failure encountered, or success if all are successful.
     *
     * <p><strong>Performance Note:</strong> This method uses lazy evaluation with suppliers
     * to provide true short-circuit behavior. Suppliers are evaluated in order and
     * evaluation stops at the first failure, making it efficient for expensive operations.
     * This is more efficient than both chaining multiple {@code and()} calls and
     * the previous eager evaluation approach.</p>
     *
     * @param suppliers the outcome suppliers to chain with AND logic
     * @return Success if all are successful, otherwise the first failure
     */
    public static EvaluationOutcome allOf(java.util.function.Supplier<EvaluationOutcome>... suppliers) {
        for (java.util.function.Supplier<EvaluationOutcome> supplier : suppliers) {
            EvaluationOutcome outcome = supplier.get();
            if (outcome instanceof Fail) {
                return outcome; // Short-circuit on first failure
            }
        }
        return success();
    }

    /**
     * Chains multiple EvaluationOutcome suppliers with OR logic.
     * Returns the first success encountered, or the last failure if all fail.
     *
     * <p><strong>Performance Note:</strong> This method uses lazy evaluation with suppliers
     * to provide true short-circuit behavior. Suppliers are evaluated in order and
     * evaluation stops at the first success, making it efficient for expensive operations.
     * This is more efficient than both chaining multiple {@code or()} calls and
     * the previous eager evaluation approach.</p>
     *
     * @param suppliers the outcome suppliers to chain with OR logic
     * @return Success if any is successful, otherwise the last failure
     */
    public static EvaluationOutcome anyOf(java.util.function.Supplier<EvaluationOutcome>... suppliers) {
        EvaluationOutcome lastFailure = null;
        for (java.util.function.Supplier<EvaluationOutcome> supplier : suppliers) {
            EvaluationOutcome outcome = supplier.get();
            if (outcome instanceof Success) {
                return outcome; // Short-circuit on first success
            }
            lastFailure = outcome;
        }
        return lastFailure != null ? lastFailure : fail("No suppliers provided");
    }

    /**
     * Convenience overload of allOf for direct EvaluationOutcome values.
     * Note: This does not provide short-circuit behavior as all arguments are evaluated.
     * For expensive operations, prefer the Supplier-based version.
     *
     * @param outcomes the outcomes to chain with AND logic
     * @return Success if all are successful, otherwise the first failure
     */
    public static EvaluationOutcome allOf(EvaluationOutcome... outcomes) {
        for (EvaluationOutcome outcome : outcomes) {
            if (outcome instanceof Fail) {
                return outcome;
            }
        }
        return success();
    }

    /**
     * Convenience overload of anyOf for direct EvaluationOutcome values.
     * Note: This does not provide short-circuit behavior as all arguments are evaluated.
     * For expensive operations, prefer the Supplier-based version.
     *
     * @param outcomes the outcomes to chain with OR logic
     * @return Success if any is successful, otherwise the last failure
     */
    public static EvaluationOutcome anyOf(EvaluationOutcome... outcomes) {
        EvaluationOutcome lastFailure = null;
        for (EvaluationOutcome outcome : outcomes) {
            if (outcome instanceof Success) {
                return outcome;
            }
            lastFailure = outcome;
        }
        return lastFailure != null ? lastFailure : fail("No outcomes provided");
    }
}

package com.java_template.application.criterion;

import com.java_template.application.entity.email_report.version_1.EmailReport;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * EmailReportRetryCriterion - Determine if failed email should be retried based on retry limits and error types
 * 
 * Transition: failed → retry (retry_email)
 * Purpose: Determine if failed email should be retried based on retry limits and error types
 */
@Component
public class EmailReportRetryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Configuration constants
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_COOLDOWN_MINUTES = 5;

    public EmailReportRetryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EmailReport retry criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EmailReport.class, this::validateRetryEligibility)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for retry eligibility
     */
    private EvaluationOutcome validateRetryEligibility(CriterionSerializer.CriterionEntityEvaluationContext<EmailReport> context) {
        EmailReport report = context.entityWithMetadata().entity();

        // Check if report entity is null
        if (report == null) {
            logger.warn("EmailReport entity is null");
            return EvaluationOutcome.fail("Email report entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!report.isValid()) {
            logger.warn("EmailReport entity is not valid");
            return EvaluationOutcome.fail("Email report entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check retry limit
        Integer retryCount = report.getRetryCount();
        if (retryCount == null) {
            retryCount = 0;
        }

        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            logger.debug("Retry limit exceeded for report: {}. Retry count: {}, Max: {}", 
                        report.getReportId(), retryCount, MAX_RETRY_ATTEMPTS);
            return EvaluationOutcome.fail(
                String.format("Maximum retry attempts exceeded. Current: %d, Max: %d", retryCount, MAX_RETRY_ATTEMPTS),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Check cooldown period
        LocalDateTime lastRetryAt = report.getLastRetryAt();
        if (lastRetryAt != null) {
            LocalDateTime now = LocalDateTime.now();
            long minutesSinceLastRetry = ChronoUnit.MINUTES.between(lastRetryAt, now);
            
            if (minutesSinceLastRetry < RETRY_COOLDOWN_MINUTES) {
                logger.debug("Still in cooldown period for report: {}. Minutes since last retry: {}, Required: {}", 
                            report.getReportId(), minutesSinceLastRetry, RETRY_COOLDOWN_MINUTES);
                return EvaluationOutcome.fail(
                    String.format("Still in cooldown period. Minutes since last retry: %d, Required: %d", 
                                 minutesSinceLastRetry, RETRY_COOLDOWN_MINUTES),
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                );
            }
        }

        // Check error type (some errors should not be retried)
        String errorMessage = report.getErrorMessage();
        if (errorMessage != null) {
            String lowerErrorMessage = errorMessage.toLowerCase();
            
            // Check for permanent errors that should not be retried
            if (lowerErrorMessage.contains("invalid email") || 
                lowerErrorMessage.contains("malformed email") ||
                lowerErrorMessage.contains("email format")) {
                logger.debug("Permanent email format error for report: {}. Error: {}", 
                            report.getReportId(), errorMessage);
                return EvaluationOutcome.fail(
                    "Permanent error - invalid email format: " + errorMessage,
                    StandardEvalReasonCategories.VALIDATION_FAILURE
                );
            }
            
            if (lowerErrorMessage.contains("blocked recipient") || 
                lowerErrorMessage.contains("recipient blocked") ||
                lowerErrorMessage.contains("blacklisted")) {
                logger.debug("Permanent recipient blocked error for report: {}. Error: {}", 
                            report.getReportId(), errorMessage);
                return EvaluationOutcome.fail(
                    "Permanent error - blocked recipient: " + errorMessage,
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                );
            }
            
            if (lowerErrorMessage.contains("authentication failed") || 
                lowerErrorMessage.contains("unauthorized") ||
                lowerErrorMessage.contains("invalid credentials")) {
                logger.debug("Permanent authentication error for report: {}. Error: {}", 
                            report.getReportId(), errorMessage);
                return EvaluationOutcome.fail(
                    "Permanent error - authentication failed: " + errorMessage,
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                );
            }
        }

        // All checks passed - retry is allowed
        logger.debug("Retry allowed for report: {}. Retry count: {}, Max: {}", 
                    report.getReportId(), retryCount, MAX_RETRY_ATTEMPTS);
        return EvaluationOutcome.success();
    }
}

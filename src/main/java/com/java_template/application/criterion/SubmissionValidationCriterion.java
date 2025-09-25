package com.java_template.application.criterion;

import com.java_template.application.entity.submission.version_1.Submission;
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

/**
 * SubmissionValidationCriterion - Validates submission data before submission
 * Checks if submission meets all requirements for review
 */
@Component
public class SubmissionValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SubmissionValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Submission validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Submission.class, this::validateSubmission)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the submission entity
     */
    private EvaluationOutcome validateSubmission(CriterionSerializer.CriterionEntityEvaluationContext<Submission> context) {
        Submission submission = context.entityWithMetadata().entity();

        // Check if submission is null (structural validation)
        if (submission == null) {
            logger.warn("Submission is null");
            return EvaluationOutcome.fail("Submission is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check basic entity validity
        if (!submission.isValid()) {
            logger.warn("Submission is not valid: {}", submission.getTitle());
            return EvaluationOutcome.fail("Submission is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check title length
        if (submission.getTitle().length() < 5) {
            logger.warn("Submission title too short: {}", submission.getTitle());
            return EvaluationOutcome.fail("Submission title must be at least 5 characters", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check description length
        if (submission.getDescription().length() < 20) {
            logger.warn("Submission description too short: {}", submission.getTitle());
            return EvaluationOutcome.fail("Submission description must be at least 20 characters", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check submission type validity
        if (!isValidSubmissionType(submission.getSubmissionType())) {
            logger.warn("Invalid submission type: {}", submission.getSubmissionType());
            return EvaluationOutcome.fail("Invalid submission type", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check priority validity
        if (!isValidPriority(submission.getPriority())) {
            logger.warn("Invalid priority: {}", submission.getPriority());
            return EvaluationOutcome.fail("Invalid priority level", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check submitter email format
        if (!isValidEmailFormat(submission.getSubmitterEmail())) {
            logger.warn("Invalid submitter email format: {}", submission.getSubmitterEmail());
            return EvaluationOutcome.fail("Invalid submitter email format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check target decision date is in the future
        if (submission.getTargetDecisionDate().isBefore(LocalDateTime.now())) {
            logger.warn("Target decision date is in the past for submission: {}", submission.getTitle());
            return EvaluationOutcome.fail("Target decision date must be in the future", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check target decision date is reasonable (not more than 2 years in the future)
        if (submission.getTargetDecisionDate().isAfter(LocalDateTime.now().plusYears(2))) {
            logger.warn("Target decision date too far in the future for submission: {}", submission.getTitle());
            return EvaluationOutcome.fail("Target decision date cannot be more than 2 years in the future", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates email format
     */
    private boolean isValidEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return email.contains("@") && email.contains(".") && 
               email.indexOf("@") > 0 && 
               email.indexOf("@") < email.lastIndexOf(".");
    }

    /**
     * Validates if the submission type is one of the allowed values
     */
    private boolean isValidSubmissionType(String type) {
        return "RESEARCH_PROPOSAL".equals(type) || 
               "CLINICAL_TRIAL".equals(type) || 
               "ETHICS_REVIEW".equals(type);
    }

    /**
     * Validates if the priority is one of the allowed values
     */
    private boolean isValidPriority(String priority) {
        return "LOW".equals(priority) || 
               "MEDIUM".equals(priority) || 
               "HIGH".equals(priority) ||
               "URGENT".equals(priority);
    }
}

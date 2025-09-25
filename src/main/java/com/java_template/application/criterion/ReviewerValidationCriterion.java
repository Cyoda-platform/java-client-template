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

/**
 * ReviewerValidationCriterion - Validates reviewer assignment
 * Checks if reviewer assignment meets business rules
 */
@Component
public class ReviewerValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReviewerValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Reviewer validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Submission.class, this::validateReviewer)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for reviewer assignment
     */
    private EvaluationOutcome validateReviewer(CriterionSerializer.CriterionEntityEvaluationContext<Submission> context) {
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

        // Check if reviewer is assigned
        if (submission.getReviewerEmail() == null || submission.getReviewerEmail().trim().isEmpty()) {
            logger.warn("No reviewer assigned to submission: {}", submission.getTitle());
            return EvaluationOutcome.fail("Reviewer must be assigned", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check reviewer email format
        if (!isValidEmailFormat(submission.getReviewerEmail())) {
            logger.warn("Invalid reviewer email format: {}", submission.getReviewerEmail());
            return EvaluationOutcome.fail("Invalid reviewer email format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check that submitter and reviewer are different
        if (submission.getSubmitterEmail().equals(submission.getReviewerEmail())) {
            logger.warn("Submitter and reviewer cannot be the same for submission: {}", submission.getTitle());
            return EvaluationOutcome.fail("Submitter and reviewer cannot be the same person", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check submitter email format (should be valid if we got here, but double-check)
        if (!isValidEmailFormat(submission.getSubmitterEmail())) {
            logger.warn("Invalid submitter email format: {}", submission.getSubmitterEmail());
            return EvaluationOutcome.fail("Invalid submitter email format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
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
}

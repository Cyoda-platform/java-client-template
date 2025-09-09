package com.java_template.application.criterion;

import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
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
import java.util.Date;

@Component
public class CommentAnalysisJobEmailFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CommentAnalysisJobEmailFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking email failure for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(CommentAnalysisJob.class, this::validateEmailFailed)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEmailFailed(CriterionSerializer.CriterionEntityEvaluationContext<CommentAnalysisJob> context) {
        CommentAnalysisJob job = context.entityWithMetadata().entity();

        if (job == null) {
            return EvaluationOutcome.fail("Job is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check for explicit email-related error
        if (job.getErrorMessage() != null && 
            job.getErrorMessage().toLowerCase().contains("email")) {
            logger.warn("Email sending failed with error: {}", job.getErrorMessage());
            return EvaluationOutcome.success();
        }

        // Check timeout (5 minutes)
        Date creationDate = context.entityWithMetadata().metadata().getCreationDate();
        if (creationDate != null) {
            LocalDateTime createdAt = creationDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            long minutesInState = ChronoUnit.MINUTES.between(createdAt, LocalDateTime.now());
            if (minutesInState > 5) {
                logger.warn("Email sending timeout - {} minutes since creation", minutesInState);
                return EvaluationOutcome.success();
            }
        }

        // Check for invalid email format
        if (job.getRecipientEmail() == null || 
            !job.getRecipientEmail().contains("@") || 
            !job.getRecipientEmail().contains(".")) {
            logger.warn("Invalid email format: {}", job.getRecipientEmail());
            return EvaluationOutcome.success();
        }

        return EvaluationOutcome.fail("No email failure detected", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}

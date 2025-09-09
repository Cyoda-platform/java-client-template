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

/**
 * CommentAnalysisJobIngestionFailedCriterion
 * 
 * Detects if ingestion has failed (API errors, network issues, etc.).
 * Used in transition: INGESTING → INGESTION_FAILED
 */
@Component
public class CommentAnalysisJobIngestionFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CommentAnalysisJobIngestionFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ingestion failure for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(CommentAnalysisJob.class, this::validateIngestionFailed)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateIngestionFailed(CriterionSerializer.CriterionEntityEvaluationContext<CommentAnalysisJob> context) {
        CommentAnalysisJob job = context.entityWithMetadata().entity();

        if (job == null) {
            logger.warn("CommentAnalysisJob is null");
            return EvaluationOutcome.fail("Job is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if job has explicit error message related to ingestion
        if (job.getErrorMessage() != null && 
            (job.getErrorMessage().toLowerCase().contains("ingestion") ||
             job.getErrorMessage().toLowerCase().contains("api") ||
             job.getErrorMessage().toLowerCase().contains("network"))) {
            logger.warn("Ingestion failed for job with explicit error: {}", job.getErrorMessage());
            return EvaluationOutcome.success(); // Success means the failure condition is met
        }

        // Check if job has been in INGESTING state for too long (timeout)
        Date creationDate = context.entityWithMetadata().metadata().getCreationDate();
        if (creationDate != null) {
            LocalDateTime createdAt = creationDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            long minutesInState = ChronoUnit.MINUTES.between(createdAt, LocalDateTime.now());
            if (minutesInState > 10) { // 10 minutes timeout
                logger.warn("Ingestion timeout for job - {} minutes since creation", minutesInState);
                return EvaluationOutcome.success(); // Success means the failure condition is met
            }
        }

        // No failure detected
        return EvaluationOutcome.fail("No ingestion failure detected", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}

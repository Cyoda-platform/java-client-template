package com.java_template.application.criterion;

import com.java_template.application.entity.job.version_1.Job;
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

@Component
public class PersistSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PersistSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null) return false;
        // Must use exact criterion name (case-sensitive)
        String operationName = modelSpec.operationName();
        return className.equals(operationName);
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             logger.warn("PersistSuccessCriterion: received null Job entity");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required identifiers
         if (job.getJobId() == null || job.getJobId().isBlank()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getSourceEndpoint() == null || job.getSourceEndpoint().isBlank()) {
             return EvaluationOutcome.fail("sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status must be present
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus().trim();

         // Only terminal states are considered a persisted outcome for this criterion
         if ("COMPLETED".equalsIgnoreCase(status)) {
             // COMPLETED must have result summary and last run timestamp
             if (job.getResultSummary() == null || job.getResultSummary().isBlank()) {
                 return EvaluationOutcome.fail("resultSummary must be provided for COMPLETED jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getLastRunAt() == null || job.getLastRunAt().isBlank()) {
                 return EvaluationOutcome.fail("lastRunAt must be provided for COMPLETED jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         if ("FAILED".equalsIgnoreCase(status)) {
             // FAILED should at least carry a result summary explaining failure and have retryCount present
             if (job.getResultSummary() == null || job.getResultSummary().isBlank()) {
                 return EvaluationOutcome.fail("resultSummary should describe failure for FAILED jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getRetryCount() == null || job.getRetryCount() < 0) {
                 return EvaluationOutcome.fail("retryCount must be non-null and non-negative for FAILED jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // Non-terminal states are not considered persisted outcomes
         return EvaluationOutcome.fail("Job is not in a terminal persisted state: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
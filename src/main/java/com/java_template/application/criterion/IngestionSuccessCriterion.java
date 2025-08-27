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
public class IngestionSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IngestionSuccessCriterion(SerializerFactory serializerFactory) {
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
        if (modelSpec == null || modelSpec.operationName() == null) {
            return false;
        }
        // Must match the exact criterion class name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             logger.warn("IngestionSuccessCriterion: received null job entity in context");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // jobId and state are required per entity definition; guard early
         if (job.getJobId() == null || job.getJobId().isBlank()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getState() == null || job.getState().isBlank()) {
             return EvaluationOutcome.fail("state is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String state = job.getState().trim().toUpperCase();

         // Success condition: explicit SUCCEEDED state with required metadata present and no error details
         if ("SUCCEEDED".equals(state)) {
             if (job.getFinishedAt() == null || job.getFinishedAt().isBlank()) {
                 return EvaluationOutcome.fail("finishedAt must be provided when state is SUCCEEDED", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getResultSummary() == null || job.getResultSummary().isBlank()) {
                 return EvaluationOutcome.fail("resultSummary must be provided when state is SUCCEEDED", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getErrorDetails() != null && !job.getErrorDetails().isBlank()) {
                 return EvaluationOutcome.fail("errorDetails must be empty for a successful ingestion", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // If explicitly failed, this criterion should fail
         if ("FAILED".equals(state)) {
             String msg = "Job is in FAILED state";
             if (job.getErrorDetails() != null && !job.getErrorDetails().isBlank()) {
                 msg += ": " + job.getErrorDetails();
             }
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // For any other state (e.g., SCHEDULED, INGESTING, NOTIFIED_SUBSCRIBERS) we do not consider ingestion successful
         return EvaluationOutcome.fail("Job is not in SUCCEEDED state (current: " + job.getState() + ")", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
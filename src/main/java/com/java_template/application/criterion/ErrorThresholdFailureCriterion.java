package com.java_template.application.criterion;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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
public class ErrorThresholdFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ErrorThresholdFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(IngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return "ErrorThresholdFailureCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob job = context.entity();
         if (job == null) {
             logger.warn("IngestionJob entity is null in criterion context");
             return EvaluationOutcome.fail("Entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields validation
         if (job.getSourceEndpoint() == null || job.getSourceEndpoint().isBlank()) {
             return EvaluationOutcome.fail("sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getSchedule() == null || job.getSchedule().isBlank()) {
             return EvaluationOutcome.fail("schedule is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality checks
         Integer processed = job.getProcessedCount();
         if (processed == null) {
             return EvaluationOutcome.fail("processedCount is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (processed < 0) {
             return EvaluationOutcome.fail("processedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String status = job.getStatus();
         String errorSummary = job.getErrorSummary();

         // Business rule: if job already marked FAILED -> fail criterion
         if (status != null && status.equalsIgnoreCase("FAILED")) {
             String msg = "Ingestion job status is FAILED";
             logger.info("Job {} marked FAILED - failing criterion", job.getJobId());
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If the job reported errors, treat as data quality failure.
         if (errorSummary != null && !errorSummary.isBlank()) {
             // If errors present but nothing processed -> stronger failure
             if (processed == 0) {
                 return EvaluationOutcome.fail("Job reported errors and processedCount is 0: " + errorSummary,
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // Otherwise still indicate data quality issue
             return EvaluationOutcome.fail("Job reported errors: " + errorSummary,
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}
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
public class ErrorThresholdSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private static final String CRITERION_NAME = "ErrorThresholdSuccessCriterion";
    private static final int MIN_PROCESSED_THRESHOLD = 5;

    public ErrorThresholdSuccessCriterion(SerializerFactory serializerFactory) {
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
        // Use exact criterion name as required by critical requirements
        return CRITERION_NAME.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();

         if (entity == null) {
             logger.warn("IngestionJob entity is null");
             return EvaluationOutcome.fail("Entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields validation
         if (entity.getJobId() == null || entity.getJobId().isBlank()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // processedCount must be present (as per entity contract) and non-negative
         if (entity.getProcessedCount() == null) {
             return EvaluationOutcome.fail("processedCount is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getProcessedCount() < 0) {
             return EvaluationOutcome.fail("processedCount must not be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String status = entity.getStatus().trim().toUpperCase();
         Integer processed = entity.getProcessedCount();
         String errorSummary = entity.getErrorSummary();

         // If job is explicitly failed -> criterion should fail
         if ("FAILED".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("Ingestion job marked as FAILED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If completed but processedCount is zero -> data quality problem
         if ("COMPLETED".equalsIgnoreCase(status) && processed == 0) {
             // Check for specific 'no data' marker in errorSummary, prefer a targeted message
             if (errorSummary != null && !errorSummary.isBlank() && errorSummary.toLowerCase().contains("no data")) {
                 return EvaluationOutcome.fail("No data was returned from source", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.fail("Job completed with zero processed records", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If there is an error summary present, evaluate severity:
         if (errorSummary != null && !errorSummary.isBlank()) {
             String lowerSummary = errorSummary.toLowerCase();
             if (lowerSummary.contains("no data")) {
                 return EvaluationOutcome.fail("No data returned from source", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // If errors reported but processed count is very low -> consider it a data quality/business issue
             if (processed < MIN_PROCESSED_THRESHOLD) {
                 return EvaluationOutcome.fail(
                     "Errors reported and processed count below acceptable threshold",
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE
                 );
             }
             // If errors exist but processed count is reasonable, treat as business rule failure (requires attention)
             return EvaluationOutcome.fail(
                 "Errors were reported during ingestion; manual review recommended",
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
             );
         }

         // If none of the failure conditions met, success.
         return EvaluationOutcome.success();
    }
}
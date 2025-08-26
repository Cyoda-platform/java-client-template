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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(IngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();

         if (entity == null) {
             logger.warn("IngestionJob entity is null in ErrorThresholdFailureCriterion");
             return EvaluationOutcome.fail("Entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         Integer processed = entity.getProcessedCount();
         String summary = entity.getErrorSummary();

         // Basic validation guard
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Rule 1: If job failed and processed no records -> data quality failure (likely systemic)
         if ("FAILED".equalsIgnoreCase(status)) {
             if (processed == null || processed == 0) {
                 return EvaluationOutcome.fail("Ingestion job failed without processing any records", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // If failed but processed some records, surface as business rule failure with summary if available
             String message = "Ingestion job failed after processing " + processed + " records";
             if (summary != null && !summary.isBlank()) {
                 message += ": " + summary;
             }
             return EvaluationOutcome.fail(message, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Rule 2: Completed but has error summary mentioning errors -> data quality failure
         if ("COMPLETED".equalsIgnoreCase(status)) {
             if (summary != null && !summary.isBlank()) {
                 String lower = summary.toLowerCase();
                 if (lower.contains("error") || lower.contains("failed") || lower.contains("exception")) {
                     return EvaluationOutcome.fail("Ingestion job completed with errors: " + summary, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
             // Completed but processedCount == 0 is suspicious -> data quality failure
             if (processed != null && processed == 0) {
                 return EvaluationOutcome.fail("Job marked COMPLETED but processedCount is zero", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // No threshold breach detected
         return EvaluationOutcome.success();
    }
}
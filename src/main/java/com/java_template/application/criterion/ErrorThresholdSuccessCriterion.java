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
    private final String className = this.getClass().getSimpleName();

    public ErrorThresholdSuccessCriterion(SerializerFactory serializerFactory) {
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
         IngestionJob job = context.entity();
         if (job == null) {
             return EvaluationOutcome.fail("IngestionJob entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If job explicitly failed -> criterion fails (business rule)
         if ("FAILED".equalsIgnoreCase(status)) {
             String summary = job.getErrorSummary();
             String msg = "Ingestion job marked FAILED" + (summary != null && !summary.isBlank() ? ": " + summary : "");
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Expect job to be completed for success evaluation
         if (!"COMPLETED".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("Ingestion job is not completed", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // For completed jobs enforce basic data quality: processedCount must exist and be > 0
         Integer processed = job.getProcessedCount();
         if (processed == null) {
             return EvaluationOutcome.fail("processedCount is missing for completed job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (processed <= 0) {
             return EvaluationOutcome.fail("No records were processed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If an error summary is present treat it as a business-rule level failure (errors exceeded threshold)
         String errorSummary = job.getErrorSummary();
         if (errorSummary != null && !errorSummary.isBlank()) {
             return EvaluationOutcome.fail("Errors reported: " + errorSummary, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}
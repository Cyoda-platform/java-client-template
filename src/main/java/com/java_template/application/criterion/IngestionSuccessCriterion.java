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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             logger.warn("Received null Job in IngestionSuccessCriterion");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Criterion only applies when job is currently ingesting
         String state = job.getState();
         if (state == null || !state.equalsIgnoreCase("INGESTING")) {
             return EvaluationOutcome.fail("Job is not in INGESTING state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Ingestion considered complete when finishedAt is set
         String finishedAt = job.getFinishedAt();
         if (finishedAt == null || finishedAt.isBlank()) {
             return EvaluationOutcome.fail("Ingestion not finished (finishedAt missing)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // processedCount must be present (even zero is acceptable)
         Integer processed = job.getProcessedCount();
         if (processed == null) {
             return EvaluationOutcome.fail("processedCount is missing after ingestion", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If there are failed records, mark as data quality failure (should go to FAILED)
         Integer failed = job.getFailedCount();
         if (failed != null && failed > 0) {
             return EvaluationOutcome.fail(
                 String.format("Ingestion completed with %d failed records", failed),
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE
             );
         }

         // If an error summary exists, treat as data quality failure
         String errorSummary = job.getErrorSummary();
         if (errorSummary != null && !errorSummary.isBlank()) {
             return EvaluationOutcome.fail("Ingestion reported error summary: " + errorSummary, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed -> ingestion succeeded
         return EvaluationOutcome.success();
    }
}
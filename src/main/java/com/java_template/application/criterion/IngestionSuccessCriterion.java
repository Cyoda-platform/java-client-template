package com.java_template.application.criterion;

import com.java_template.application.entity.batchjob.version_1.BatchJob;
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
            .evaluateEntity(BatchJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<BatchJob> context) {
         BatchJob entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         String summary = entity.getSummary();
         String startedAt = entity.getStartedAt();
         String finishedAt = entity.getFinishedAt();

         // Basic presence validation
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("BatchJob.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If job explicitly failed -> fail the criterion (ingestion did not succeed)
         if ("FAILED".equalsIgnoreCase(status)) {
             String reason = "Batch job marked as FAILED";
             if (summary != null && !summary.isBlank()) reason += ": " + summary;
             return EvaluationOutcome.fail(reason, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If job completed, require finishedAt timestamp
         if ("COMPLETED".equalsIgnoreCase(status)) {
             if (finishedAt == null || finishedAt.isBlank()) {
                 return EvaluationOutcome.fail("Completed job missing finishedAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // completed -> success
             return EvaluationOutcome.success();
         }

         // If job is in generating/reporting or running states, consider ingestion success when
         // ingestion summary and startedAt are present (non-terminal but indicates ingestion occurred)
         if ("GENERATING_REPORT".equalsIgnoreCase(status)
             || "RUNNING".equalsIgnoreCase(status)
             || "VALIDATING".equalsIgnoreCase(status)) {

             if (startedAt == null || startedAt.isBlank()) {
                 return EvaluationOutcome.fail("Job missing startedAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             if (summary != null && summary.toLowerCase().contains("ingested")) {
                 return EvaluationOutcome.success();
             } else {
                 return EvaluationOutcome.fail("Ingestion summary not present or does not indicate ingested users", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // For any other status values, return a business rule failure to indicate unexpected state
         return EvaluationOutcome.fail("Unexpected job status: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
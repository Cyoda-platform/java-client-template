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
public class FetchSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchSuccessCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("IngestionJob entity is null in FetchSuccessCriterion");
             return EvaluationOutcome.fail("IngestionJob is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic required fields validation
         if (job.getRequestedBy() == null || job.getRequestedBy().isBlank()) {
             return EvaluationOutcome.fail("requestedBy is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
             return EvaluationOutcome.fail("sourceUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getStartedAt() == null || job.getStartedAt().isBlank()) {
             return EvaluationOutcome.fail("startedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Interpret fetch success by job status and presence of completion timestamp.
         if ("COMPLETED".equalsIgnoreCase(status)) {
             if (job.getCompletedAt() == null || job.getCompletedAt().isBlank()) {
                 return EvaluationOutcome.fail("completedAt must be present for COMPLETED jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             // If summary exists, validate counts are present and non-negative
             if (job.getSummary() != null) {
                 Integer created = job.getSummary().getCreated();
                 Integer updated = job.getSummary().getUpdated();
                 Integer failed = job.getSummary().getFailed();
                 if (created == null || updated == null || failed == null) {
                     return EvaluationOutcome.fail("summary counts must be present when summary is provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (created < 0 || updated < 0 || failed < 0) {
                     return EvaluationOutcome.fail("summary counts must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 // If fetch produced no records at all, still consider success but warn (handled by reason attachment)
                 if (created == 0 && updated == 0 && failed == 0) {
                     logger.info("IngestionJob completed but produced no records: sourceUrl={}", job.getSourceUrl());
                 }
             }

             return EvaluationOutcome.success();
         }

         if ("FAILED".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("Ingestion job reported FAILED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Any other intermediate status is considered not successful for fetch criterion
         return EvaluationOutcome.fail("Ingestion job not completed (status=" + status + ")", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
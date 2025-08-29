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
public class SourceUnavailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SourceUnavailableCriterion(SerializerFactory serializerFactory) {
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
        // Must match the exact criterion name
        return modelSpec.operationName() != null && modelSpec.operationName().equals(className);
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob job = context.entity();

         // Required fields validation
         if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
             String msg = "sourceUrl is required for ingestion job" + (job.getJobId() != null ? " [" + job.getJobId() + "]" : "");
             logger.debug(msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getScheduleCron() == null || job.getScheduleCron().isBlank()) {
             String msg = "scheduleCron is required for ingestion job" + (job.getJobId() != null ? " [" + job.getJobId() + "]" : "");
             logger.debug(msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data formats quality check (if provided)
         String formats = job.getDataFormats();
         if (formats != null && !formats.isBlank()) {
             String lower = formats.toLowerCase();
             if (!lower.contains("json") && !lower.contains("xml")) {
                 String msg = "dataFormats must include JSON or XML for ingestion job" + (job.getJobId() != null ? " [" + job.getJobId() + "]" : "");
                 logger.debug("{} - provided: {}", msg, formats);
                 return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rule: if job status indicates a failure, treat source as unavailable
         String status = job.getStatus();
         if (status != null && status.equalsIgnoreCase("FAILED")) {
             String msg = "Ingestion job status is FAILED; source may be unavailable for job" + (job.getJobId() != null ? " [" + job.getJobId() + "]" : "");
             logger.info(msg + " - sourceUrl={}", job.getSourceUrl());
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If lastRunAt is missing, log a warning (do not fail criterion).
         if (job.getLastRunAt() == null || job.getLastRunAt().isBlank()) {
             String warn = "Ingestion job has not run yet (no lastRunAt) for job" + (job.getJobId() != null ? " [" + job.getJobId() + "]" : "");
             // Attempt to surface as a warning via logs; ReasonAttachmentStrategy.toWarnings() is configured on completion
             logger.warn(warn);
             // Note: Not invoking context-specific mutation method because such API may not be present across serializer versions.
         }

         // All checks passed — consider source available for workflow progression
         return EvaluationOutcome.success();
    }
}
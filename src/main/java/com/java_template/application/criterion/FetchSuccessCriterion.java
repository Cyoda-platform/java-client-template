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

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.format.DateTimeParseException;

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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(IngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob job = context.entity();
         if (job == null) {
             logger.warn("IngestionJob entity is null in FetchSuccessCriterion");
             return EvaluationOutcome.fail("IngestionJob is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required basic fields
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

         // Validate sourceUrl format
         try {
             new URL(job.getSourceUrl());
         } catch (MalformedURLException e) {
             logger.debug("Invalid sourceUrl format: {}", job.getSourceUrl(), e);
             return EvaluationOutcome.fail("sourceUrl is not a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate timestamp formats and chronology where possible
         Instant startedAtInstant;
         try {
             startedAtInstant = Instant.parse(job.getStartedAt());
         } catch (DateTimeParseException e) {
             logger.debug("startedAt is not a valid ISO-8601 timestamp: {}", job.getStartedAt(), e);
             return EvaluationOutcome.fail("startedAt is not a valid ISO-8601 timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if ("COMPLETED".equalsIgnoreCase(status)) {
             if (job.getCompletedAt() == null || job.getCompletedAt().isBlank()) {
                 return EvaluationOutcome.fail("completedAt must be present for COMPLETED jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             Instant completedAtInstant;
             try {
                 completedAtInstant = Instant.parse(job.getCompletedAt());
             } catch (DateTimeParseException e) {
                 logger.debug("completedAt is not a valid ISO-8601 timestamp: {}", job.getCompletedAt(), e);
                 return EvaluationOutcome.fail("completedAt is not a valid ISO-8601 timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (completedAtInstant.isBefore(startedAtInstant)) {
                 logger.warn("completedAt is before startedAt for job with sourceUrl={}", job.getSourceUrl());
                 return EvaluationOutcome.fail("completedAt must not be before startedAt", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             // If summary exists, validate counts are present and non-negative and consistent
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
                 // Basic consistency check: failed should not exceed total attempts (created + updated + failed)
                 int total = created + updated + failed;
                 if (failed > total) {
                     return EvaluationOutcome.fail("summary failed count is inconsistent with total counts", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 // Informational: if no records produced at all, log for awareness (handled as warning attachment)
                 if (created == 0 && updated == 0 && failed == 0) {
                     logger.info("IngestionJob completed but produced no records: sourceUrl={}", job.getSourceUrl());
                 }
             } else {
                 // summary not present — acceptable but warn (warnings attached by serializer)
                 logger.info("IngestionJob completed without summary: sourceUrl={}", job.getSourceUrl());
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
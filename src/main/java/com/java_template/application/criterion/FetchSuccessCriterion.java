package com.java_template.application.criterion;

import com.java_template.application.entity.petsyncjob.version_1.PetSyncJob;
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

import java.util.Map;

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
            .evaluateEntity(PetSyncJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Requirement: supports() MUST use exact criterion name (case sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetSyncJob> context) {
         PetSyncJob entity = context.entity();
         if (entity == null) {
             logger.warn("PetSyncJob entity is null");
             return EvaluationOutcome.fail("PetSyncJob entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields validation using only available getters
         String id = entity.getId();
         String source = entity.getSource();
         String status = entity.getStatus();
         String startTime = entity.getStartTime();
         Map<String, Object> config = entity.getConfig();
         Integer fetchedCount = entity.getFetchedCount();
         String errorMessage = entity.getErrorMessage();

         if (id == null || id.isBlank()) {
             logger.warn("PetSyncJob.id is missing or blank");
             return EvaluationOutcome.fail("Job id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (source == null || source.isBlank()) {
             logger.warn("PetSyncJob.source is missing or blank for job {}", id);
             return EvaluationOutcome.fail("Job source is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (status == null || status.isBlank()) {
             logger.warn("PetSyncJob.status is missing or blank for job {}", id);
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (startTime == null || startTime.isBlank()) {
             logger.warn("PetSyncJob.startTime is missing or blank for job {}", id);
             return EvaluationOutcome.fail("Job start time is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (config == null) {
             logger.warn("PetSyncJob.config is missing for job {}", id);
             return EvaluationOutcome.fail("Job config is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If job explicitly failed during fetch
         if ("failed".equalsIgnoreCase(status)) {
             String msg = "Fetch failed";
             if (errorMessage != null && !errorMessage.isBlank()) {
                 msg = "Fetch failed: " + errorMessage;
             }
             logger.warn("PetSyncJob {} marked as failed during fetch: {}", id, errorMessage);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Only consider success for jobs that are in FETCHING (ready to move to PARSING) or COMPLETED (edge-case)
         if ("fetching".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) {
             if (fetchedCount == null) {
                 logger.warn("PetSyncJob {} has null fetchedCount while in status {}", id, status);
                 return EvaluationOutcome.fail("fetched_count is missing after fetch", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (fetchedCount < 0) {
                 logger.warn("PetSyncJob {} has negative fetchedCount: {}", id, fetchedCount);
                 return EvaluationOutcome.fail("fetched_count must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (errorMessage != null && !errorMessage.isBlank()) {
                 logger.warn("PetSyncJob {} contains an error message despite non-failed status: {}", id, errorMessage);
                 return EvaluationOutcome.fail("Fetch produced error: " + errorMessage, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // If no items fetched, treat as data-quality issue — parsing step likely unnecessary
             if (fetchedCount == 0) {
                 logger.info("PetSyncJob {} fetched 0 items; nothing to parse", id);
                 return EvaluationOutcome.fail("No items fetched", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // passed all checks -> success
             logger.info("PetSyncJob {} fetch considered successful (status={}, fetchedCount={})", id, status, fetchedCount);
             return EvaluationOutcome.success();
         }

         // For other statuses (pending, parsing, persisting, etc.) this criterion is not satisfied
         logger.warn("PetSyncJob {} is in status '{}' which is not eligible for fetch-success transition", id, status);
         return EvaluationOutcome.fail("Job not in fetching/completed state", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
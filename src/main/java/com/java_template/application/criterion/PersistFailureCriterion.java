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

@Component
public class PersistFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PersistFailureCriterion(SerializerFactory serializerFactory) {
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
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetSyncJob> context) {
         PetSyncJob job = context.entity();
         if (job == null) {
             logger.warn("PersistFailureCriterion: received null PetSyncJob entity");
             return EvaluationOutcome.fail("PetSyncJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus();
         if (status == null || status.isBlank()) {
             logger.warn("PersistFailureCriterion: job {} has no status", job.getId());
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If the job is marked failed, ensure failure details are present and sensible.
         if ("failed".equalsIgnoreCase(status)) {
             if (job.getErrorMessage() == null || job.getErrorMessage().isBlank()) {
                 logger.warn("PersistFailureCriterion: failed job {} missing errorMessage", job.getId());
                 return EvaluationOutcome.fail("error_message is required for failed job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getEndTime() == null || job.getEndTime().isBlank()) {
                 logger.warn("PersistFailureCriterion: failed job {} missing endTime", job.getId());
                 return EvaluationOutcome.fail("end_time is required for failed job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getFetchedCount() == null) {
                 logger.warn("PersistFailureCriterion: failed job {} missing fetchedCount", job.getId());
                 return EvaluationOutcome.fail("fetched_count is required for failed job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getFetchedCount() < 0) {
                 logger.warn("PersistFailureCriterion: failed job {} has negative fetchedCount {}", job.getId(), job.getFetchedCount());
                 return EvaluationOutcome.fail("fetched_count cannot be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // Return a failure outcome reflecting that the job indeed failed (business-level failure)
             return EvaluationOutcome.fail("Persisting job failed: " + job.getErrorMessage(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // During persisting status ensure we at least have sensible counters and start time
         if ("persisting".equalsIgnoreCase(status)) {
             if (job.getStartTime() == null || job.getStartTime().isBlank()) {
                 logger.warn("PersistFailureCriterion: persisting job {} missing startTime", job.getId());
                 return EvaluationOutcome.fail("start_time is required for persisting job", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (job.getFetchedCount() == null) {
                 logger.warn("PersistFailureCriterion: persisting job {} missing fetchedCount", job.getId());
                 return EvaluationOutcome.fail("fetched_count is required while persisting", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getFetchedCount() < 0) {
                 logger.warn("PersistFailureCriterion: persisting job {} has negative fetchedCount {}", job.getId(), job.getFetchedCount());
                 return EvaluationOutcome.fail("fetched_count cannot be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // For other statuses no persistent-failure related problems detected
         return EvaluationOutcome.success();
    }
}
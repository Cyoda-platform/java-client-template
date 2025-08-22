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
public class FetchFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetSyncJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetSyncJob> context) {
         PetSyncJob job = context.entity();

         // Basic required status check
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             logger.debug("PetSyncJob {} missing status", job.getId());
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus().trim().toLowerCase();

         // This criterion focuses on fetch failures: when a job reports 'failed' ensure error details are present.
         if ("failed".equals(status)) {
             if (job.getErrorMessage() == null || job.getErrorMessage().isBlank()) {
                 logger.debug("PetSyncJob {} marked failed but missing errorMessage", job.getId());
                 return EvaluationOutcome.fail("Failed job must include errorMessage", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (job.getEndTime() == null || job.getEndTime().isBlank()) {
                 logger.debug("PetSyncJob {} marked failed but missing endTime", job.getId());
                 return EvaluationOutcome.fail("Failed job must include endTime", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // A failed job is considered a business/process-level failure (fetch failed)
             return EvaluationOutcome.fail("Pet sync job has failed: " + job.getErrorMessage(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Additional sanity checks for fetching state: ensure startTime exists when fetching
         if ("fetching".equals(status)) {
             if (job.getStartTime() == null || job.getStartTime().isBlank()) {
                 logger.debug("PetSyncJob {} in fetching state but missing startTime", job.getId());
                 return EvaluationOutcome.fail("Fetching job must have startTime", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // If not a failure or invalid fetching state, the criterion passes.
         return EvaluationOutcome.success();
    }
}
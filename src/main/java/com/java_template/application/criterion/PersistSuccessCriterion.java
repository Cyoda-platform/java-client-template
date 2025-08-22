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
public class PersistSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PersistSuccessCriterion(SerializerFactory serializerFactory) {
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
        // must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetSyncJob> context) {
         PetSyncJob entity = context.entity();
         if (entity == null) {
             logger.warn("PersistSuccessCriterion: received null entity in evaluation context");
             return EvaluationOutcome.fail("PetSyncJob entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic structural validation using the entity's own isValid() implementation
         if (!entity.isValid()) {
             String id = entity.getId();
             String msg = "PetSyncJob is invalid: missing required fields" + (id != null ? " (id=" + id + ")" : "");
             logger.debug("PersistSuccessCriterion: {}", msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // fetchedCount must be non-negative if present
         Integer fetchedCount = entity.getFetchedCount();
         if (fetchedCount != null && fetchedCount < 0) {
             return EvaluationOutcome.fail("fetchedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If the job reported an error -> failing outcome
         String errorMessage = entity.getErrorMessage();
         if (errorMessage != null && !errorMessage.isBlank()) {
             return EvaluationOutcome.fail("Job reported error: " + errorMessage, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Success condition: status must be 'completed'
         if ("completed".equalsIgnoreCase(status)) {
             return EvaluationOutcome.success();
         }

         // If the job explicitly failed
         if ("failed".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("Job status is failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Not completed yet -> criterion not satisfied
         return EvaluationOutcome.fail("Persist not completed, current status: " + status, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}
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
public class ParseSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ParseSuccessCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetSyncJob> context) {
         PetSyncJob entity = context.entity();

         // Validate presence of required technical id
         if (entity.getId() == null || entity.getId().isBlank()) {
             logger.debug("ParseSuccessCriterion failed: missing job id");
             return EvaluationOutcome.fail("PetSyncJob id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Ensure the job is in the expected parsing state
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             logger.debug("ParseSuccessCriterion failed: missing status for job {}", entity.getId());
             return EvaluationOutcome.fail("PetSyncJob status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"parsing".equalsIgnoreCase(status)) {
             logger.debug("ParseSuccessCriterion failed: job {} is in status '{}', expected 'parsing'", entity.getId(), status);
             return EvaluationOutcome.fail("PetSyncJob is not in parsing state", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If parser reported an error -> business rule failure
         String errorMessage = entity.getErrorMessage();
         if (errorMessage != null && !errorMessage.isBlank()) {
             logger.debug("ParseSuccessCriterion failed: job {} has errorMessage='{}'", entity.getId(), errorMessage);
             return EvaluationOutcome.fail("Parsing produced an error: " + errorMessage, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Ensure parsing produced at least one record to persist
         Integer fetchedCount = entity.getFetchedCount();
         if (fetchedCount == null || fetchedCount <= 0) {
             logger.debug("ParseSuccessCriterion data quality failure: job {} fetchedCount={}", entity.getId(), fetchedCount);
             return EvaluationOutcome.fail("No parsed items to persist (fetched_count is zero or missing)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         logger.debug("ParseSuccessCriterion succeeded for job {} (fetched_count={})", entity.getId(), fetchedCount);
         return EvaluationOutcome.success();
    }
}
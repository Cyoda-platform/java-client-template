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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetSyncJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return "PersistSuccessCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetSyncJob> context) {
         PetSyncJob job = context.entity();
         if (job == null) {
             return EvaluationOutcome.fail("PetSyncJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required field validation
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Persist success criterion: job must be completed without errors and with a valid fetched count and endTime
         if (!"completed".equalsIgnoreCase(job.getStatus())) {
             return EvaluationOutcome.fail("Job status is not 'completed'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if (job.getFetchedCount() == null) {
             return EvaluationOutcome.fail("fetchedCount is missing for completed job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (job.getFetchedCount() < 0) {
             return EvaluationOutcome.fail("fetchedCount is negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (job.getEndTime() == null || job.getEndTime().isBlank()) {
             return EvaluationOutcome.fail("endTime is required for completed job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (job.getErrorMessage() != null && !job.getErrorMessage().isBlank()) {
             return EvaluationOutcome.fail("Completed job must not have an errorMessage", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}
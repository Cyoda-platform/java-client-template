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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetSyncJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetSyncJob> context) {
         PetSyncJob job = context.entity();

         if (job == null) {
             logger.warn("PetSyncJob entity is null in PersistFailureCriterion");
             return EvaluationOutcome.fail("Job entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If job indicates a failure in the persisting step, report failure with reason
         if ("failed".equalsIgnoreCase(status)) {
             String err = job.getErrorMessage();
             if (err == null || err.isBlank()) {
                 return EvaluationOutcome.fail("Job marked as failed but no errorMessage provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // Provide the operational failure reason collected from the job
             return EvaluationOutcome.fail("Persisting failed: " + err, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Otherwise consider the criterion successful (no persist failure detected)
         return EvaluationOutcome.success();
    }
}
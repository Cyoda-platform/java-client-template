package com.java_template.application.criterion;

import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
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
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetIngestionJob> context) {
         PetIngestionJob entity = context.entity();

         // Basic validation: status must be present
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Job status is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus().trim();

         // If job is explicitly marked FAILED, ensure errors are present and report failure
         if ("FAILED".equalsIgnoreCase(status)) {
             if (entity.getErrors() == null || entity.getErrors().isEmpty()) {
                 return EvaluationOutcome.fail("Job marked FAILED but no error details provided", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             int errs = entity.getErrors().size();
             return EvaluationOutcome.fail("Persist stage failed with " + errs + " error(s)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If currently persisting but produced errors and no records persisted -> treat as persist failure
         if ("PERSISTING".equalsIgnoreCase(status)) {
             boolean hasErrors = entity.getErrors() != null && !entity.getErrors().isEmpty();
             boolean noProcessed = entity.getProcessedCount() == null || entity.getProcessedCount() == 0;
             if (hasErrors && noProcessed) {
                 return EvaluationOutcome.fail("Persisting produced errors and no records were persisted", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // If completed, ensure completedAt and processedCount look sane
         if ("COMPLETED".equalsIgnoreCase(status)) {
             if (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank()) {
                 return EvaluationOutcome.fail("Job marked COMPLETED but completedAt is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (entity.getProcessedCount() == null || entity.getProcessedCount() < 0) {
                 return EvaluationOutcome.fail("Job marked COMPLETED but processedCount is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // No persist-failure conditions detected
         return EvaluationOutcome.success();
    }
}
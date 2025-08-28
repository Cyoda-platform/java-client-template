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
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetIngestionJob> context) {
         PetIngestionJob entity = context.entity();

         // Basic presence checks
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Job status is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: status must indicate completion for a successful persist
         if (!"COMPLETED".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("Ingestion job is not completed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality: completedAt must be present when status is COMPLETED
         if (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank()) {
             return EvaluationOutcome.fail("completedAt must be provided for a completed job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Data quality: processedCount must exist and be non-negative
         if (entity.getProcessedCount() == null) {
             return EvaluationOutcome.fail("processedCount is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getProcessedCount() < 0) {
             return EvaluationOutcome.fail("processedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Data quality: there should be no recorded errors for a successful persist
         if (entity.getErrors() != null && !entity.getErrors().isEmpty()) {
             return EvaluationOutcome.fail("Errors present in job result", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}
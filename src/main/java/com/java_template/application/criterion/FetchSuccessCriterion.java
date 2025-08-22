package com.java_template.application.criterion;

import com.java_template.application.entity.petenrichmentjob.version_1.PetEnrichmentJob;
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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetEnrichmentJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetEnrichmentJob> context) {
         PetEnrichmentJob entity = context.entity();
         // Validate required inputs
         if (entity.getPetSource() == null || entity.getPetSource().isBlank()) {
             return EvaluationOutcome.fail("petSource is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFetchedCount() == null) {
             return EvaluationOutcome.fail("fetchedCount is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFetchedCount() < 0) {
             return EvaluationOutcome.fail("fetchedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getErrors() == null) {
             return EvaluationOutcome.fail("errors list must be present (can be empty)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!entity.getErrors().isEmpty()) {
             return EvaluationOutcome.fail("Fetch completed with errors", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // If job status explicitly indicates failure treat as failure
         if ("FAILED".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("job status indicates failure", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
        return EvaluationOutcome.success();
    }
}
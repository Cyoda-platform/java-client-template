package com.java_template.application.criterion;

import com.java_template.application.entity.adoptionorder.version_1.AdoptionOrder;
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
public class PassEligibilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PassEligibilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(AdoptionOrder.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionOrder> context) {
         AdoptionOrder entity = context.entity();

         // Basic presence validations
         if (entity.getPetId() == null || entity.getPetId().isBlank()) {
            return EvaluationOutcome.fail("petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUserId() == null || entity.getUserId().isBlank()) {
            return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRequestedDate() == null || entity.getRequestedDate().isBlank()) {
            return EvaluationOutcome.fail("requestedDate is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPickupMethod() == null || entity.getPickupMethod().isBlank()) {
            return EvaluationOutcome.fail("pickupMethod is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule checks
         // PassEligibilityCriterion should only evaluate orders in the "requested" state
         if (!"requested".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("Order must be in 'requested' state to be eligible", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If already approved or completed, it's not eligible
         if (entity.getApprovedDate() != null && !entity.getApprovedDate().isBlank()) {
             return EvaluationOutcome.fail("Order already approved", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         if (entity.getCompletedDate() != null && !entity.getCompletedDate().isBlank()) {
             return EvaluationOutcome.fail("Order already completed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If all checks pass, the order passes this criterion.
         return EvaluationOutcome.success();
    }
}
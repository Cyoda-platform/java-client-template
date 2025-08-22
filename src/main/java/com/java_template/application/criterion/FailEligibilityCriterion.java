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
public class FailEligibilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FailEligibilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
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
         if (entity == null) {
             return EvaluationOutcome.fail("AdoptionOrder entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields validation (use existing getters only)
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("Order id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
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

         // Business rule validations
         String pickup = entity.getPickupMethod();
         if (!"inStore".equals(pickup) && !"homeDelivery".equals(pickup)) {
             return EvaluationOutcome.fail("pickupMethod must be 'inStore' or 'homeDelivery'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Eligibility check should run for orders that are in 'requested' state.
         // If the order is already in another terminal or progressed state, treat as business rule failure.
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Allowed lifecycle states (per functional requirements)
         if (!status.equals("requested") && !status.equals("under_review") && !status.equals("approved")
             && !status.equals("declined") && !status.equals("cancelled") && !status.equals("completed")) {
             return EvaluationOutcome.fail("status contains an unknown value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         // For the specific purpose of eligibility failing criterion, if the order is not in 'requested' state it's not eligible to be checked here.
         if (!status.equals("requested")) {
             return EvaluationOutcome.fail("Order is not in 'requested' state for eligibility evaluation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If all basic validations pass, do not fail eligibility here (other processors/criteria will check pet/user)
         return EvaluationOutcome.success();
    }
}
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
public class OnPickupConfirmedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OnPickupConfirmedCriterion(SerializerFactory serializerFactory) {
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

         // Basic validation: status must be present
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Order status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: pickup can only be confirmed for orders in 'pending' state
         if (!"pending".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("Order must be in 'pending' state to confirm pickup", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // approvedDate must be present (order should have been approved before moving to pending)
         if (entity.getApprovedDate() == null || entity.getApprovedDate().isBlank()) {
             return EvaluationOutcome.fail("Order must have an approvedDate before pickup can be confirmed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // completedDate must be provided when confirming pickup
         if (entity.getCompletedDate() == null || entity.getCompletedDate().isBlank()) {
             return EvaluationOutcome.fail("completedDate is required to confirm pickup", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // pickupMethod must be one of the supported values
         String method = entity.getPickupMethod();
         if (method == null || method.isBlank()) {
             return EvaluationOutcome.fail("pickupMethod is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         boolean supported = "inStore".equalsIgnoreCase(method) || "homeDelivery".equalsIgnoreCase(method);
         if (!supported) {
             return EvaluationOutcome.fail("Unsupported pickupMethod. Allowed values: inStore, homeDelivery", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}
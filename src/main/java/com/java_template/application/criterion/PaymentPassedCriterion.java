package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
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
public class PaymentPassedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentPassedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
         Order entity = context.entity();

         // Basic required fields validation using only available getters
         if (entity == null) {
             logger.warn("Order entity is null in PaymentPassedCriterion");
             return EvaluationOutcome.fail("Order entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String buyerContact = entity.getBuyerContact();
         if (buyerContact == null || buyerContact.isBlank()) {
             return EvaluationOutcome.fail("buyerContact is required for payment validation", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String buyerName = entity.getBuyerName();
         if (buyerName == null || buyerName.isBlank()) {
             return EvaluationOutcome.fail("buyerName is required for payment validation", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String petId = entity.getPetId();
         if (petId == null || petId.isBlank()) {
             return EvaluationOutcome.fail("petId reference is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String type = entity.getType();
         if (type == null || type.isBlank()) {
             return EvaluationOutcome.fail("order type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("order status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String placedAt = entity.getPlacedAt();
         if (placedAt == null || placedAt.isBlank()) {
             // placedAt missing could indicate data quality issue (timestamp expected for placed orders)
             return EvaluationOutcome.fail("placedAt timestamp is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: Payment validation should only be applied to newly placed orders
         // Expect status to be "PLACED" before payment is evaluated
         if (!"PLACED".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("order not in PLACED state, current status: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All local checks passed. Note: availability of the pet is checked elsewhere in OrderValidationProcessor
         return EvaluationOutcome.success();
    }
}
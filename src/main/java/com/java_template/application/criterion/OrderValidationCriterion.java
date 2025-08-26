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
public class OrderValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderValidationCriterion(SerializerFactory serializerFactory) {
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
        if (modelSpec == null) return false;
        // Use exact criterion name (case-sensitive) as required
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
         Order entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required top-level fields
         if (entity.getOrderId() == null || entity.getOrderId().isBlank()) {
             return EvaluationOutcome.fail("orderId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCustomerUserId() == null || entity.getCustomerUserId().isBlank()) {
             return EvaluationOutcome.fail("customerUserId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Items presence and basic validation
         if (entity.getItems() == null || entity.getItems().isEmpty()) {
             return EvaluationOutcome.fail("Order must contain at least one item", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         double computedSubtotal = 0.0;
         int idx = 0;
         for (Order.Item it : entity.getItems()) {
             idx++;
             if (it == null) {
                 return EvaluationOutcome.fail("Item at position " + idx + " is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (it.getProductSku() == null || it.getProductSku().isBlank()) {
                 return EvaluationOutcome.fail("productSku is required for item at position " + idx, StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (it.getQuantity() == null || it.getQuantity() <= 0) {
                 return EvaluationOutcome.fail("quantity must be > 0 for item with sku " + it.getProductSku(), StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (it.getUnitPrice() == null || it.getUnitPrice() < 0.0) {
                 return EvaluationOutcome.fail("unitPrice must be >= 0 for item with sku " + it.getProductSku(), StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             computedSubtotal += it.getQuantity() * it.getUnitPrice();
         }

         // Subtotal presence and consistency
         if (entity.getSubtotal() == null) {
             return EvaluationOutcome.fail("subtotal is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (Math.abs(entity.getSubtotal() - computedSubtotal) > 0.01) {
             return EvaluationOutcome.fail(
                 String.format("subtotal mismatch: expected %.2f calculated %.2f", entity.getSubtotal(), computedSubtotal),
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE
             );
         }

         // Total checks
         if (entity.getTotal() == null) {
             return EvaluationOutcome.fail("total is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getTotal() < 0.0) {
             return EvaluationOutcome.fail("total must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getTotal() + 0.01 < entity.getSubtotal()) {
             return EvaluationOutcome.fail("total must be at least subtotal", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: Order should be in Created state before validation leads to confirmation
         String status = entity.getStatus();
         if (!"Created".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("order must be in 'Created' status to be validated", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}
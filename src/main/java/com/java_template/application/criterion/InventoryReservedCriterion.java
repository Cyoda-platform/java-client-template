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
public class InventoryReservedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public InventoryReservedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
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

         if (entity == null) {
             return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // itemsSnapshot must be present and non-empty
         if (entity.getItemsSnapshot() == null || entity.getItemsSnapshot().isEmpty()) {
             return EvaluationOutcome.fail("Order must contain at least one item", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate items and compute total
         double computedTotal = 0.0;
         int idx = 0;
         for (Order.Item it : entity.getItemsSnapshot()) {
             idx++;
             if (it == null) {
                 return EvaluationOutcome.fail("Order contains null item at position " + idx, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // Use existing item validation if available
             try {
                 if (!it.isValid()) {
                     return EvaluationOutcome.fail("Order contains invalid item at position " + idx, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             } catch (Exception ex) {
                 // Fallback to manual checks if isValid throws or is not usable
                 if (it.getProductId() == null || it.getProductId().isBlank()) {
                     return EvaluationOutcome.fail("Order item missing productId at position " + idx, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (it.getQuantity() == null || it.getQuantity() <= 0) {
                     return EvaluationOutcome.fail("Order item has invalid quantity at position " + idx, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (it.getUnitPrice() == null || it.getUnitPrice() < 0) {
                     return EvaluationOutcome.fail("Order item has invalid unitPrice at position " + idx, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }

             // accumulate
             Double up = it.getUnitPrice() != null ? it.getUnitPrice() : 0.0;
             Integer q = it.getQuantity() != null ? it.getQuantity() : 0;
             computedTotal += up * q;
         }

         // totalAmount must be present and non-negative
         Double totalAmount = entity.getTotalAmount();
         if (totalAmount == null || totalAmount < 0) {
             return EvaluationOutcome.fail("Order.totalAmount is required and must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Allow small rounding differences
         if (Math.abs(computedTotal - totalAmount) > 0.01) {
             return EvaluationOutcome.fail(
                 String.format("Order totalAmount mismatch. Computed=%.2f, Declared=%.2f", computedTotal, totalAmount),
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE
             );
         }

         // Status must be present and one of expected workflow states
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Order.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Valid order workflow states (as per functional requirements)
         String st = status.trim().toUpperCase();
         if (!"WAITING_TO_FULFILL".equals(st) && !"PICKING".equals(st) && !"SENT".equals(st)) {
             return EvaluationOutcome.fail("Order.status is not a valid state for order workflow: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If all checks pass, we consider the inventory reservation criterion satisfied (note: actual inventory reservations
         // are handled by processors; this criterion validates order structure and integrity).
         return EvaluationOutcome.success();
    }
}
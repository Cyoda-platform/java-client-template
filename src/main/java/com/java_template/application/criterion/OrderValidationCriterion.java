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

/**
 * OrderValidationCriterion - Validate order details before approval
 * 
 * Transition: approve_order (placed → approved)
 * Purpose: Validate order details before approval
 */
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
        logger.debug("Checking Order validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();

        // Check if order is null (structural validation)
        if (order == null) {
            logger.warn("Order is null");
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!order.isValid()) {
            logger.warn("Order is not valid");
            return EvaluationOutcome.fail("Order is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // 1. Validate order has at least one item
        if (order.getItems() == null || order.getItems().isEmpty()) {
            logger.warn("Order has no items: {}", order.getOrderId());
            return EvaluationOutcome.fail("Order must have at least one item", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // 2. Check if all items have valid petId references
        for (Order.OrderItem item : order.getItems()) {
            if (item.getPetId() == null || item.getPetId().trim().isEmpty()) {
                logger.warn("Order item has invalid petId: {}", order.getOrderId());
                return EvaluationOutcome.fail("All order items must have valid pet ID", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // 3. Verify total amount equals sum of (quantity × unitPrice) for all items
        double calculatedTotal = order.getItems().stream()
                .mapToDouble(item -> {
                    if (item.getQuantity() != null && item.getUnitPrice() != null) {
                        return item.getQuantity() * item.getUnitPrice();
                    }
                    return item.getTotalPrice() != null ? item.getTotalPrice() : 0.0;
                })
                .sum();

        if (Math.abs(calculatedTotal - order.getTotalAmount()) > 0.01) {
            logger.warn("Order total amount mismatch: calculated={}, order={}", calculatedTotal, order.getTotalAmount());
            return EvaluationOutcome.fail("Order total amount does not match item totals", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // 4. Validate shipping address has all required fields
        Order.Address address = order.getShippingAddress();
        if (address == null) {
            logger.warn("Order has no shipping address: {}", order.getOrderId());
            return EvaluationOutcome.fail("Order must have shipping address", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (address.getStreet() == null || address.getStreet().trim().isEmpty() ||
            address.getCity() == null || address.getCity().trim().isEmpty() ||
            address.getState() == null || address.getState().trim().isEmpty() ||
            address.getZipCode() == null || address.getZipCode().trim().isEmpty() ||
            address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            logger.warn("Order shipping address is incomplete: {}", order.getOrderId());
            return EvaluationOutcome.fail("Shipping address must have all required fields", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // 5. Check if order amount is within acceptable limits (< $10,000)
        if (order.getTotalAmount() >= 10000.0) {
            logger.warn("Order amount exceeds limit: {} for order {}", order.getTotalAmount(), order.getOrderId());
            return EvaluationOutcome.fail("Order amount exceeds maximum limit of $10,000", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Order validation passed for order: {}", order.getOrderId());
        return EvaluationOutcome.success();
    }
}

package com.java_template.application.criterion;

import com.java_template.application.entity.Order;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class OrderValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
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

        Order order = context.entity();

        // Validation logic based on business requirements
        // 1. totalAmount must be positive and equal to sum of orderItems quantity*price
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return EvaluationOutcome.fail("Total amount must be positive", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        List<com.java_template.application.entity.OrderItem> items = order.getOrderItems();
        if (items == null || items.isEmpty()) {
            return EvaluationOutcome.fail("Order must contain at least one order item", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        BigDecimal calculatedTotal = BigDecimal.ZERO;
        for (com.java_template.application.entity.OrderItem item : items) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                return EvaluationOutcome.fail("Order item quantity must be positive", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (item.getPriceAtPurchase() == null || item.getPriceAtPurchase().compareTo(BigDecimal.ZERO) < 0) {
                return EvaluationOutcome.fail("Order item price must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            BigDecimal itemTotal = item.getPriceAtPurchase().multiply(BigDecimal.valueOf(item.getQuantity()));
            calculatedTotal = calculatedTotal.add(itemTotal);
        }

        if (order.getTotalAmount().compareTo(calculatedTotal) != 0) {
            return EvaluationOutcome.fail("Total amount does not match sum of order items", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // 2. status must be PENDING or other allowed initial statuses
        if (order.getStatus() == null || !(order.getStatus().equalsIgnoreCase("PENDING") || order.getStatus().equalsIgnoreCase("SHIPPED") || order.getStatus().equalsIgnoreCase("DELIVERED") || order.getStatus().equalsIgnoreCase("CANCELLED"))) {
            return EvaluationOutcome.fail("Order status is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}

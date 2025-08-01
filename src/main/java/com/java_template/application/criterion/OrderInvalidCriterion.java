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

import java.util.List;

@Component
public class OrderInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderInvalidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
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

        // This criterion represents invalid conditions for order
        // If any validation condition from OrderValidCriterion fails, this may pass

        // Here, we validate that the order is invalid if totalAmount is null or <= 0
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return EvaluationOutcome.success();
        }

        // Or if orderItems is null or empty
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return EvaluationOutcome.success();
        }

        // Or if any order item has invalid quantity or price
        for (com.java_template.application.entity.OrderItem item : order.getOrderItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                return EvaluationOutcome.success();
            }
            if (item.getPriceAtPurchase() == null || item.getPriceAtPurchase().compareTo(java.math.BigDecimal.ZERO) < 0) {
                return EvaluationOutcome.success();
            }
        }

        // Or if totalAmount does not match sum of order items
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        for (com.java_template.application.entity.OrderItem item : order.getOrderItems()) {
            java.math.BigDecimal itemTotal = item.getPriceAtPurchase().multiply(java.math.BigDecimal.valueOf(item.getQuantity()));
            sum = sum.add(itemTotal);
        }
        if (order.getTotalAmount().compareTo(sum) != 0) {
            return EvaluationOutcome.success();
        }

        // Or if status is invalid
        if (order.getStatus() == null || !(order.getStatus().equalsIgnoreCase("PENDING") || order.getStatus().equalsIgnoreCase("SHIPPED") || order.getStatus().equalsIgnoreCase("DELIVERED") || order.getStatus().equalsIgnoreCase("CANCELLED"))) {
            return EvaluationOutcome.success();
        }

        return EvaluationOutcome.fail("Order is valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}

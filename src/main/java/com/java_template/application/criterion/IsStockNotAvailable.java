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
public class IsStockNotAvailable implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsStockNotAvailable(SerializerFactory serializerFactory) {
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

        // Business logic: Verify product stock NOT availability for each order item
        List<com.java_template.application.entity.OrderItem> items = order.getOrderItems();
        if (items == null || items.isEmpty()) {
            return EvaluationOutcome.fail("Order must contain at least one order item", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        for (com.java_template.application.entity.OrderItem item : items) {
            String productId = item.getProductId();
            Integer quantity = item.getQuantity();

            if (productId == null || productId.isBlank()) {
                return EvaluationOutcome.fail("Order item productId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (quantity == null || quantity <= 0) {
                return EvaluationOutcome.fail("Order item quantity must be positive", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Simulate stock check: Here we assume a method or service call to check stock
            boolean inStock = checkStockForProduct(productId, quantity);
            if (inStock) {
                return EvaluationOutcome.fail("Product " + productId + " is available in stock, expected not available", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }

    private boolean checkStockForProduct(String productId, Integer quantity) {
        // Placeholder logic for stock checking
        // In real implementation, this would query inventory or stock management system
        // Here we simulate always not available for demonstration
        return false;
    }
}

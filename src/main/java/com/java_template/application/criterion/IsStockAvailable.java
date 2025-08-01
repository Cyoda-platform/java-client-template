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
public class IsStockAvailable implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsStockAvailable(SerializerFactory serializerFactory) {
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

        // Validate that all order items have quantity > 0 and productId not null/blank
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return EvaluationOutcome.fail("Order must have at least one order item", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        for (var item : order.getOrderItems()) {
            if (item.getProductId() == null || item.getProductId().isBlank()) {
                return EvaluationOutcome.fail("Order item productId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                return EvaluationOutcome.fail("Order item quantity must be greater than zero", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // Business logic: Check stock availability for each order item (placeholder logic)
        // In real scenario, this might call inventory service or check against stock data
        // Here we simulate stock check by assuming all quantities <= 100 are available

        for (var item : order.getOrderItems()) {
            if (item.getQuantity() > 100) {
                return EvaluationOutcome.fail("Stock not available for productId: " + item.getProductId(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}

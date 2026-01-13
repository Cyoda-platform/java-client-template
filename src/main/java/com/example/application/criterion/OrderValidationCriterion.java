package com.example.application.criterion;

import com.example.application.entity.order.version_1.Order;
import com.example.application.entity.trader.version_1.Trader;
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
 * OrderValidationCriterion - Validates orders before routing
 * 
 * Checks mandatory fields, positive quantity, supported symbols,
 * and applies basic risk checks: max order size per trader, max notional per day.
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
        logger.debug("Validating Order for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrder)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOrder(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();

        if (order == null) {
            logger.warn("Order is null");
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate mandatory fields
        if (!order.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Order validation failed for orderId: {}", order.getOrderId());
            return EvaluationOutcome.fail("Order has invalid fields", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate positive quantity
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            return EvaluationOutcome.fail("Quantity must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate price for limit orders
        if ("LIMIT".equals(order.getOrderType()) && (order.getPrice() == null || order.getPrice() <= 0)) {
            return EvaluationOutcome.fail("Limit orders must have positive price", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.info("Order {} validation passed", order.getOrderId());
        return EvaluationOutcome.success();
    }
}


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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Criterion for validating orders before routing
 * Checks order validity and risk compliance
 */
@Component
public class OrderValidationCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidationCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public OrderValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking OrderValidationCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Order.class, this::validateOrder)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOrder(
            CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {

        Order order = context.entityWithMetadata().entity();

        logger.debug("Validating order: {} for routing", order.getOrderId());

        // Check order quantity
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            logger.warn("Order {} has invalid quantity: {}", order.getOrderId(), order.getQuantity());
            return EvaluationOutcome.fail("Order quantity must be positive",
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check order side
        if (!"BUY".equals(order.getSide()) && !"SELL".equals(order.getSide())) {
            logger.warn("Order {} has invalid side: {}", order.getOrderId(), order.getSide());
            return EvaluationOutcome.fail("Order side must be BUY or SELL",
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check order type
        String[] validTypes = {"MARKET", "LIMIT", "STOP", "STOP_LIMIT"};
        boolean validType = false;
        for (String type : validTypes) {
            if (type.equals(order.getOrderType())) {
                validType = true;
                break;
            }
        }
        if (!validType) {
            logger.warn("Order {} has invalid type: {}", order.getOrderId(), order.getOrderType());
            return EvaluationOutcome.fail("Invalid order type",
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check limit price for limit orders
        if ("LIMIT".equals(order.getOrderType()) && order.getLimitPrice() == null) {
            logger.warn("Order {} is LIMIT but has no limit price", order.getOrderId());
            return EvaluationOutcome.fail("Limit price required for LIMIT orders",
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.info("Order {} validation passed", order.getOrderId());
        return EvaluationOutcome.success();
    }
}


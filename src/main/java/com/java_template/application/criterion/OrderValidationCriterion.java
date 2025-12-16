package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.CriterionCalculationRequest;
import org.cyoda.cloud.api.event.processing.CriterionCalculationResponse;
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
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public CriterionCalculationResponse evaluate(CyodaEventContext<CriterionCalculationRequest> context) {
        CriterionCalculationRequest request = context.getEvent();
        logger.info("Evaluating OrderValidationCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::evaluateOrder)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private boolean evaluateOrder(
            CriterionSerializer.CriterionEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Evaluating order: {} for validation", order.getOrderId());

        // Check order quantity
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            logger.warn("Order {} has invalid quantity: {}", order.getOrderId(), order.getQuantity());
            return false;
        }

        // Check order side
        if (!"BUY".equals(order.getSide()) && !"SELL".equals(order.getSide())) {
            logger.warn("Order {} has invalid side: {}", order.getOrderId(), order.getSide());
            return false;
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
            return false;
        }

        // Check limit price for limit orders
        if ("LIMIT".equals(order.getOrderType()) && order.getLimitPrice() == null) {
            logger.warn("Order {} is LIMIT but has no limit price", order.getOrderId());
            return false;
        }

        logger.info("Order {} validation passed", order.getOrderId());
        return true;
    }
}


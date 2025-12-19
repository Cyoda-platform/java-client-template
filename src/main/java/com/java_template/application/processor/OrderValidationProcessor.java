package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OrderValidationProcessor - Validates order parameters and business rules
 */
@Component
public class OrderValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OrderValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating Order for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidOrder, "Invalid order")
                .map(this::validateOrderRules)
                .complete();
    }

    private EntityWithMetadata<Order> validateOrderRules(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.getEntity();
        
        // Validate order type
        if (!isValidOrderType(order.getOrderType())) {
            logger.warn("Invalid order type: {}", order.getOrderType());
            throw new IllegalArgumentException("Invalid order type: " + order.getOrderType());
        }
        
        // Validate price for limit orders
        if ("LIMIT".equals(order.getOrderType()) && (order.getPrice() == null || order.getPrice() <= 0)) {
            logger.warn("Limit order must have positive price");
            throw new IllegalArgumentException("Limit order must have positive price");
        }
        
        logger.info("Order validation passed for: {}", order.getOrderId());
        return orderWithMetadata;
    }

    private boolean isValidOrderType(String orderType) {
        return orderType != null && (
            "MARKET".equals(orderType) ||
            "LIMIT".equals(orderType) ||
            "STOP".equals(orderType) ||
            "IOC".equals(orderType) ||
            "FOK".equals(orderType)
        );
    }

    private boolean isValidOrder(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.getEntity();
        return order.getOrderId() != null && order.getQuantity() != null && order.getQuantity() > 0;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}


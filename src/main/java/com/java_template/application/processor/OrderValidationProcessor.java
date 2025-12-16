package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Processor for validating orders
 * Performs pre-trade checks: order size, price limits, risk rules
 */
@Component
public class OrderValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderValidationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing OrderValidation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::validateOrder)
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

    private EntityWithMetadata<Order> validateOrder(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Validating order: {} for {} shares of {}", 
                   order.getOrderId(), order.getQuantity(), order.getInstrumentSymbol());

        // Validate order quantity
        if (order.getQuantity() <= 0) {
            logger.error("Invalid order quantity: {}", order.getQuantity());
            throw new IllegalArgumentException("Order quantity must be positive");
        }

        // Validate limit price for limit orders
        if ("LIMIT".equals(order.getOrderType()) && order.getLimitPrice() == null) {
            logger.error("Limit price required for LIMIT order");
            throw new IllegalArgumentException("Limit price required for LIMIT orders");
        }

        // Validate stop price for stop orders
        if ("STOP".equals(order.getOrderType()) && order.getStopPrice() == null) {
            logger.error("Stop price required for STOP order");
            throw new IllegalArgumentException("Stop price required for STOP orders");
        }

        // Validate side
        if (!"BUY".equals(order.getSide()) && !"SELL".equals(order.getSide())) {
            logger.error("Invalid order side: {}", order.getSide());
            throw new IllegalArgumentException("Order side must be BUY or SELL");
        }

        // Update timestamp
        order.setUpdatedAt(LocalDateTime.now());

        logger.info("Order {} validated successfully", order.getOrderId());
        return entityWithMetadata;
    }
}


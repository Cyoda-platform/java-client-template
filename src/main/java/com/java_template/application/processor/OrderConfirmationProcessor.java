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
 * Processor for confirming order execution
 * Finalizes order and triggers post-trade processing
 */
@Component
public class OrderConfirmationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderConfirmationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderConfirmationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing OrderConfirmation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::confirmOrder)
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

    private EntityWithMetadata<Order> confirmOrder(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Confirming order: {} with {} shares filled", 
                   order.getOrderId(), order.getQuantityFilled());

        // Verify execution details
        if (order.getQuantityFilled() == null || order.getQuantityFilled() == 0) {
            logger.error("No quantity filled for order: {}", order.getOrderId());
            throw new IllegalStateException("Order confirmation requires filled quantity");
        }

        if (order.getAverageExecutionPrice() == null || order.getAverageExecutionPrice() <= 0) {
            logger.error("Invalid execution price for order: {}", order.getOrderId());
            throw new IllegalStateException("Order confirmation requires valid execution price");
        }

        // Update timestamp
        order.setUpdatedAt(LocalDateTime.now());

        logger.info("Order {} confirmed with {} shares at {}", 
                   order.getOrderId(), order.getQuantityFilled(), order.getAverageExecutionPrice());

        return entityWithMetadata;
    }
}


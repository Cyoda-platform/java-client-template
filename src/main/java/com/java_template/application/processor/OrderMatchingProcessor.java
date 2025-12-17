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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * OrderMatchingProcessor - Attempts to match orders in the order book
 * Implements matching logic for limit and market orders
 */
@Component
public class OrderMatchingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderMatchingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderMatchingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Matching order for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        return order != null && order.isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<Order> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Attempting to match order: {} on market: {}", order.getOrderId(), order.getMarketId());

        // Simulate order matching logic
        // In production, this would query the order book and find matching orders
        
        // For now, simulate partial fill
        BigDecimal matchedQuantity = order.getQuantity().multiply(new BigDecimal("0.5"));
        order.setFilled(matchedQuantity);
        order.setRemaining(order.getQuantity().subtract(matchedQuantity));

        logger.info("Order {} matched: filled={}, remaining={}", 
            order.getOrderId(), order.getFilled(), order.getRemaining());

        return entityWithMetadata;
    }
}


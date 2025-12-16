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
import java.util.UUID;

/**
 * Processor for routing orders to execution venues
 * Determines routing destination and sends order to exchange adapter
 */
@Component
public class OrderRoutingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderRoutingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderRoutingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing OrderRouting for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::routeOrder)
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

    private EntityWithMetadata<Order> routeOrder(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();
        UUID currentEntityId = entityWithMetadata.metadata().getId();

        logger.debug("Routing order: {} to exchange", order.getOrderId());

        // Determine routing destination based on instrument
        String routingDestination = determineRoutingDestination(order);
        order.setRoutingDestination(routingDestination);

        // Generate external order ID
        String externalOrderId = "EXT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        order.setExternalOrderId(externalOrderId);

        // Update timestamp
        order.setUpdatedAt(LocalDateTime.now());

        logger.info("Order {} routed to {} with external ID: {}", 
                   order.getOrderId(), routingDestination, externalOrderId);

        return entityWithMetadata;
    }

    private String determineRoutingDestination(Order order) {
        // Simple routing logic - can be enhanced with more sophisticated rules
        // In production, this would check exchange listings, liquidity, etc.
        
        if (order.getInstrumentSymbol().contains("NYSE")) {
            return "NYSE";
        } else if (order.getInstrumentSymbol().contains("NASDAQ")) {
            return "NASDAQ";
        } else {
            return "NASDAQ"; // Default to NASDAQ
        }
    }
}


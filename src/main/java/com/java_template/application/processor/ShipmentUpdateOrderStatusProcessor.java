package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * ShipmentUpdateOrderStatusProcessor - Updates corresponding order status when shipment status changes.
 * 
 * Transitions: READY_TO_SEND, MARK_SENT, MARK_DELIVERED
 * 
 * Business Logic:
 * - Maps shipment state to order transition
 * - Updates corresponding order status
 * - Updates shipment timestamp
 */
@Component
public class ShipmentUpdateOrderStatusProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentUpdateOrderStatusProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ShipmentUpdateOrderStatusProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment status update for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Shipment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid shipment entity wrapper")
                .map(this::processShipmentStatusUpdate)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Shipment> entityWithMetadata) {
        Shipment shipment = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return shipment != null && technicalId != null && 
               shipment.getOrderId() != null && !shipment.getOrderId().trim().isEmpty();
    }

    /**
     * Main business logic for shipment status update
     */
    private EntityWithMetadata<Shipment> processShipmentStatusUpdate(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Shipment> context) {

        EntityWithMetadata<Shipment> entityWithMetadata = context.entityResponse();
        Shipment shipment = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing shipment status update for shipment: {} in state: {}", 
                   shipment.getShipmentId(), currentState);

        // Get the order associated with this shipment
        Order order = getOrderByOrderId(shipment.getOrderId());

        // Map shipment state to order transition
        String orderTransition = mapShipmentStateToOrderTransition(currentState);

        // Update order if transition is needed
        if (orderTransition != null) {
            updateOrderStatus(order, orderTransition);
        }

        // Update shipment timestamp
        shipment.setUpdatedAt(LocalDateTime.now());

        logger.info("Shipment {} status updated, order {} transition: {}", 
                   shipment.getShipmentId(), order.getOrderId(), orderTransition);

        return entityWithMetadata;
    }

    /**
     * Gets order by orderId
     */
    private Order getOrderByOrderId(String orderId) {
        try {
            ModelSpec orderModelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);
            
            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    orderModelSpec, orderId, "orderId", Order.class);
            
            return orderWithMetadata.entity();
            
        } catch (Exception e) {
            logger.error("Failed to find order with orderId: {}", orderId);
            throw new IllegalArgumentException("Order not found: " + orderId, e);
        }
    }

    /**
     * Maps shipment state to corresponding order transition
     */
    private String mapShipmentStateToOrderTransition(String shipmentState) {
        if (shipmentState == null) {
            return null;
        }

        switch (shipmentState.toUpperCase()) {
            case "WAITING_TO_SEND":
                return "READY_TO_SEND";
            case "SENT":
                return "MARK_SENT";
            case "DELIVERED":
                return "MARK_DELIVERED";
            default:
                logger.debug("No order transition mapping for shipment state: {}", shipmentState);
                return null;
        }
    }

    /**
     * Updates order status with the specified transition
     */
    private void updateOrderStatus(Order order, String transition) {
        try {
            ModelSpec orderModelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);
            
            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    orderModelSpec, order.getOrderId(), "orderId", Order.class);
            
            // Update order timestamp
            order.setUpdatedAt(LocalDateTime.now());
            
            // Update order with the specified transition
            entityService.update(orderWithMetadata.metadata().getId(), order, transition);
            
            logger.debug("Updated order {} with transition: {}", order.getOrderId(), transition);
            
        } catch (Exception e) {
            logger.error("Failed to update order {} with transition: {}", order.getOrderId(), transition);
            throw new RuntimeException("Order update failed: " + order.getOrderId(), e);
        }
    }
}

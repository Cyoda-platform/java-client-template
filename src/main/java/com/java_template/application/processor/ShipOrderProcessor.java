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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

/**
 * ABOUTME: ShipOrderProcessor handles carrier assignment, tracking number generation,
 * and shipment event creation during the transition from Packed to Shipped state.
 */
@Component
public class ShipOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ShipOrderProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processShipOrder)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or Order entity is null");
            return false;
        }

        Order order = entityWithMetadata.entity();
        
        // Validate order is in correct state for shipping
        String currentState = entityWithMetadata.metadata().getState();
        if (!"Packed".equals(currentState)) {
            logger.error("Order is not in Packed state for shipping. Current state: {}, orderId: {}", 
                        currentState, order.getOrderId());
            return false;
        }

        // Validate all line items are packed
        if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
            logger.error("No line items found for orderId: {}", order.getOrderId());
            return false;
        }

        boolean allItemsPacked = order.getLineItems().stream()
                .allMatch(item -> "packed".equals(item.getFulfilmentStatus()));
        
        if (!allItemsPacked) {
            logger.error("Not all line items are packed for orderId: {}", order.getOrderId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<Order> processShipOrder(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();
        logger.info("Processing shipping for order with orderId: {}", order.getOrderId());

        try {
            // Initialize shipment if not present
            if (order.getShipment() == null) {
                order.setShipment(new Order.Shipment());
            }

            Order.Shipment shipment = order.getShipment();

            // Generate shipment ID if not present
            if (shipment.getShipmentId() == null || shipment.getShipmentId().trim().isEmpty()) {
                shipment.setShipmentId("SHP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }

            // Assign carrier if not present (simplified carrier selection)
            if (shipment.getCarrier() == null || shipment.getCarrier().trim().isEmpty()) {
                shipment.setCarrier(selectCarrier(order));
            }

            // Set service level if not present
            if (shipment.getServiceLevel() == null || shipment.getServiceLevel().trim().isEmpty()) {
                shipment.setServiceLevel("standard");
            }

            // Generate tracking number if not present
            if (shipment.getTrackingNumber() == null || shipment.getTrackingNumber().trim().isEmpty()) {
                shipment.setTrackingNumber(generateTrackingNumber(shipment.getCarrier()));
            }

            // Set estimated delivery (3-5 business days for standard)
            if (shipment.getEstimatedDelivery() == null) {
                shipment.setEstimatedDelivery(LocalDateTime.now().plusDays(4));
            }

            // Initialize shipment events if not present
            if (shipment.getEvents() == null) {
                shipment.setEvents(new ArrayList<>());
            }

            // Add picked up event
            Order.ShipmentEvent pickedUpEvent = new Order.ShipmentEvent();
            pickedUpEvent.setEventType("picked_up");
            pickedUpEvent.setDescription("Package picked up by carrier");
            pickedUpEvent.setTimestamp(LocalDateTime.now());
            pickedUpEvent.setLocation("Fulfillment Center");
            shipment.getEvents().add(pickedUpEvent);

            // Update line items to shipped status
            order.getLineItems().forEach(item -> item.setFulfilmentStatus("shipped"));

            logger.info("Order shipped successfully - orderId: {}, shipmentId: {}, trackingNumber: {}, carrier: {}", 
                       order.getOrderId(), shipment.getShipmentId(), shipment.getTrackingNumber(), shipment.getCarrier());

            return new EntityWithMetadata<>(order, entityWithMetadata.metadata());

        } catch (Exception e) {
            logger.error("Error processing shipping for orderId: {}", order.getOrderId(), e);
            
            // Revert fulfillment status on error
            if (order.getLineItems() != null) {
                order.getLineItems().forEach(item -> item.setFulfilmentStatus("packed"));
            }
            
            throw new RuntimeException("Failed to ship order: " + e.getMessage(), e);
        }
    }

    private String selectCarrier(Order order) {
        // Simplified carrier selection logic
        // In a real implementation, this would consider factors like:
        // - Shipping address
        // - Package weight/dimensions
        // - Service level requirements
        // - Cost optimization
        
        String channel = order.getChannel();
        if ("marketplace".equals(channel)) {
            return "FedEx";
        } else if ("store".equals(channel)) {
            return "UPS";
        } else {
            return "USPS"; // Default for web orders
        }
    }

    private String generateTrackingNumber(String carrier) {
        // Generate carrier-specific tracking number format
        String prefix;
        switch (carrier.toLowerCase()) {
            case "fedex":
                prefix = "FX";
                break;
            case "ups":
                prefix = "1Z";
                break;
            case "usps":
                prefix = "US";
                break;
            default:
                prefix = "TK";
        }
        
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}

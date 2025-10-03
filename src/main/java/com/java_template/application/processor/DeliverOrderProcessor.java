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

/**
 * ABOUTME: DeliverOrderProcessor handles delivery confirmation and final shipment event creation
 * during the transition from Shipped to Delivered state.
 */
@Component
public class DeliverOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliverOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public DeliverOrderProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processDeliverOrder)
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
        
        // Validate order is in correct state for delivery
        String currentState = entityWithMetadata.metadata().getState();
        if (!"Shipped".equals(currentState)) {
            logger.error("Order is not in Shipped state for delivery. Current state: {}, orderId: {}", 
                        currentState, order.getOrderId());
            return false;
        }

        // Validate shipment information exists
        if (order.getShipment() == null) {
            logger.error("Shipment information is missing for orderId: {}", order.getOrderId());
            return false;
        }

        // Validate tracking number exists
        if (order.getShipment().getTrackingNumber() == null || 
            order.getShipment().getTrackingNumber().trim().isEmpty()) {
            logger.error("Tracking number is missing for orderId: {}", order.getOrderId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<Order> processDeliverOrder(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();
        logger.info("Processing delivery for order with orderId: {}", order.getOrderId());

        try {
            Order.Shipment shipment = order.getShipment();

            // Add delivery event
            Order.ShipmentEvent deliveredEvent = new Order.ShipmentEvent();
            deliveredEvent.setEventType("delivered");
            deliveredEvent.setDescription("Package delivered successfully");
            deliveredEvent.setTimestamp(LocalDateTime.now());
            
            // Set delivery location (simplified - could be from customer address)
            if (order.getCustomer() != null && order.getCustomer().getAddresses() != null && 
                !order.getCustomer().getAddresses().isEmpty()) {
                Order.Address deliveryAddress = order.getCustomer().getAddresses().stream()
                    .filter(addr -> "shipping".equals(addr.getType()))
                    .findFirst()
                    .orElse(order.getCustomer().getAddresses().get(0));
                
                deliveredEvent.setLocation(deliveryAddress.getCity() + ", " + deliveryAddress.getState());
            } else {
                deliveredEvent.setLocation("Customer Address");
            }

            shipment.getEvents().add(deliveredEvent);

            // Update line items to delivered status
            order.getLineItems().forEach(item -> item.setFulfilmentStatus("delivered"));

            logger.info("Order delivered successfully - orderId: {}, shipmentId: {}, trackingNumber: {}", 
                       order.getOrderId(), shipment.getShipmentId(), shipment.getTrackingNumber());

            return new EntityWithMetadata<>(order, entityWithMetadata.metadata());

        } catch (Exception e) {
            logger.error("Error processing delivery for orderId: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to deliver order: " + e.getMessage(), e);
        }
    }
}

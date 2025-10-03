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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Processor to handle order shipment
 * Assigns carrier, generates tracking number, and creates shipment events
 */
@Component
public class ShipOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ShipOrderProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::processOrderShipment)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Order> processOrderShipment(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing shipment for order: {}", order.getOrderId());

        // Initialize shipment if not exists
        if (order.getShipment() == null) {
            order.setShipment(new Order.Shipment());
        }

        Order.Shipment shipment = order.getShipment();

        // Generate shipment details
        generateShipmentDetails(shipment, order);

        // Set shipping address from customer default address
        setShippingAddress(shipment, order);

        // Update line item fulfillment status
        updateLineItemFulfillmentStatus(order);

        // Create initial shipment event
        createShipmentEvent(shipment, "picked_up", "Package picked up from warehouse", "PICKUP");

        // Update order timestamp
        order.setUpdatedTimestamp(LocalDateTime.now());
        order.setLastUpdatedBy("ShipOrderProcessor");

        logger.info("Order {} shipped successfully with tracking number: {}", 
                   order.getOrderId(), shipment.getTrackingNumber());

        return entityWithMetadata;
    }

    private void generateShipmentDetails(Order.Shipment shipment, Order order) {
        // Generate shipment ID
        if (shipment.getShipmentId() == null) {
            shipment.setShipmentId("SHP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        // Assign carrier based on service level or default
        if (shipment.getCarrier() == null) {
            shipment.setCarrier(selectCarrier(shipment.getServiceLevel()));
        }

        // Set default service level if not provided
        if (shipment.getServiceLevel() == null) {
            shipment.setServiceLevel("standard");
        }

        // Generate tracking number
        if (shipment.getTrackingNumber() == null) {
            shipment.setTrackingNumber(generateTrackingNumber(shipment.getCarrier()));
        }

        // Calculate estimated delivery
        if (shipment.getEstimatedDelivery() == null) {
            shipment.setEstimatedDelivery(calculateEstimatedDelivery(shipment.getServiceLevel()));
        }
    }

    private String selectCarrier(String serviceLevel) {
        // Simple carrier selection logic
        if ("express".equals(serviceLevel) || "overnight".equals(serviceLevel)) {
            return "FedEx";
        } else if ("standard".equals(serviceLevel)) {
            return "UPS";
        } else {
            return "USPS";
        }
    }

    private String generateTrackingNumber(String carrier) {
        String prefix;
        switch (carrier) {
            case "FedEx":
                prefix = "FX";
                break;
            case "UPS":
                prefix = "1Z";
                break;
            case "USPS":
                prefix = "US";
                break;
            default:
                prefix = "TK";
        }
        
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private LocalDateTime calculateEstimatedDelivery(String serviceLevel) {
        LocalDateTime now = LocalDateTime.now();
        
        switch (serviceLevel) {
            case "overnight":
                return now.plusDays(1);
            case "express":
                return now.plusDays(2);
            case "standard":
                return now.plusDays(5);
            default:
                return now.plusDays(7);
        }
    }

    private void setShippingAddress(Order.Shipment shipment, Order order) {
        if (shipment.getShippingAddress() == null && order.getCustomer() != null 
            && order.getCustomer().getAddresses() != null) {
            
            // Find default address or use first address
            Order.Address defaultAddress = order.getCustomer().getAddresses().stream()
                    .filter(Order.Address::isDefault)
                    .findFirst()
                    .orElse(order.getCustomer().getAddresses().get(0));
            
            shipment.setShippingAddress(defaultAddress);
        }
    }

    private void updateLineItemFulfillmentStatus(Order order) {
        for (Order.LineItem lineItem : order.getLineItems()) {
            if ("reserved".equals(lineItem.getFulfilmentStatus()) || 
                "packed".equals(lineItem.getFulfilmentStatus())) {
                lineItem.setFulfilmentStatus("shipped");
            }
        }
    }

    private void createShipmentEvent(Order.Shipment shipment, String status, String description, String eventCode) {
        if (shipment.getEvents() == null) {
            shipment.setEvents(new ArrayList<>());
        }

        Order.ShipmentEvent event = new Order.ShipmentEvent();
        event.setTimestamp(LocalDateTime.now());
        event.setStatus(status);
        event.setDescription(description);
        event.setEventCode(eventCode);
        event.setLocation("Warehouse");

        shipment.getEvents().add(event);
    }
}

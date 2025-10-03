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

/**
 * Processor to handle order delivery confirmation
 * Updates shipment status and line item fulfillment status
 */
@Component
public class DeliverOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliverOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DeliverOrderProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::processOrderDelivery)
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

    private EntityWithMetadata<Order> processOrderDelivery(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing delivery for order: {}", order.getOrderId());

        // Update shipment delivery information
        if (order.getShipment() != null) {
            order.getShipment().setActualDelivery(LocalDateTime.now());
            
            // Add delivery event
            createDeliveryEvent(order.getShipment());
        }

        // Update line item fulfillment status
        updateLineItemFulfillmentStatus(order);

        // Update order timestamp
        order.setUpdatedTimestamp(LocalDateTime.now());
        order.setLastUpdatedBy("DeliverOrderProcessor");

        logger.info("Order {} delivered successfully", order.getOrderId());

        return entityWithMetadata;
    }

    private void createDeliveryEvent(Order.Shipment shipment) {
        Order.ShipmentEvent deliveryEvent = new Order.ShipmentEvent();
        deliveryEvent.setTimestamp(LocalDateTime.now());
        deliveryEvent.setStatus("delivered");
        deliveryEvent.setDescription("Package delivered successfully");
        deliveryEvent.setEventCode("DELIVERED");
        deliveryEvent.setLocation("Customer Address");

        if (shipment.getEvents() != null) {
            shipment.getEvents().add(deliveryEvent);
        }
    }

    private void updateLineItemFulfillmentStatus(Order order) {
        for (Order.LineItem lineItem : order.getLineItems()) {
            if ("shipped".equals(lineItem.getFulfilmentStatus())) {
                lineItem.setFulfilmentStatus("delivered");
            }
        }
    }
}

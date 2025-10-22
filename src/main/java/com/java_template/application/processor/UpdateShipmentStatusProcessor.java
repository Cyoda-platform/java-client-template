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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ABOUTME: Processor for updating shipment status and synchronizing order status
 * based on shipment progress through the fulfillment lifecycle.
 */
@Component
public class UpdateShipmentStatusProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateShipmentStatusProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UpdateShipmentStatusProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Shipment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid shipment entity wrapper")
                .map(this::updateShipmentStatus)
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
        return shipment != null && shipment.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Update shipment status and synchronize with order
     */
    private EntityWithMetadata<Shipment> updateShipmentStatus(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Shipment> context) {

        EntityWithMetadata<Shipment> entityWithMetadata = context.entityResponse();
        Shipment shipment = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Updating shipment status for shipment: {} in state: {}", shipment.getShipmentId(), currentState);

        // Update shipment line quantities based on status
        updateShipmentLineQuantities(shipment, currentState);

        // Synchronize order status with shipment status
        synchronizeOrderStatus(shipment);

        // Update timestamp
        shipment.setUpdatedAt(LocalDateTime.now());

        logger.info("Shipment {} status updated to: {}", shipment.getShipmentId(), shipment.getStatus());

        return entityWithMetadata;
    }

    /**
     * Update shipment line quantities based on status
     */
    private void updateShipmentLineQuantities(Shipment shipment, String currentState) {
        if (shipment.getLines() != null) {
            for (Shipment.ShipmentLine line : shipment.getLines()) {
                switch (currentState) {
                    case "waiting_to_send":
                        // Mark all items as picked
                        line.setQtyPicked(line.getQtyOrdered());
                        break;
                    case "sent":
                        // Mark all items as shipped
                        line.setQtyShipped(line.getQtyOrdered());
                        break;
                    case "delivered":
                        // Ensure all quantities are consistent
                        line.setQtyPicked(line.getQtyOrdered());
                        line.setQtyShipped(line.getQtyOrdered());
                        break;
                }
            }
        }
    }

    /**
     * Synchronize order status with shipment status
     */
    private void synchronizeOrderStatus(Shipment shipment) {
        try {
            // Find the associated order
            ModelSpec orderModelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    orderModelSpec, shipment.getOrderId(), "orderId", Order.class);

            if (orderWithMetadata != null) {
                Order order = orderWithMetadata.entity();
                String newOrderStatus = mapShipmentStatusToOrderStatus(shipment.getStatus());
                String orderTransition = mapShipmentStatusToOrderTransition(shipment.getStatus());

                if (newOrderStatus != null && !newOrderStatus.equals(order.getStatus())) {
                    order.setStatus(newOrderStatus);
                    order.setUpdatedAt(LocalDateTime.now());

                    entityService.update(orderWithMetadata.metadata().getId(), order, orderTransition);
                    logger.debug("Synchronized order {} status to: {}", order.getOrderId(), newOrderStatus);
                }
            } else {
                logger.warn("Order not found for shipment: {}", shipment.getOrderId());
            }
        } catch (Exception e) {
            logger.error("Failed to synchronize order status for shipment: {}", shipment.getShipmentId(), e);
        }
    }

    /**
     * Map shipment status to order status
     */
    private String mapShipmentStatusToOrderStatus(String shipmentStatus) {
        return switch (shipmentStatus) {
            case "PICKING" -> "PICKING";
            case "WAITING_TO_SEND" -> "WAITING_TO_SEND";
            case "SENT" -> "SENT";
            case "DELIVERED" -> "DELIVERED";
            default -> null;
        };
    }

    /**
     * Map shipment status to order transition
     */
    private String mapShipmentStatusToOrderTransition(String shipmentStatus) {
        return switch (shipmentStatus) {
            case "PICKING" -> "start_picking";
            case "WAITING_TO_SEND" -> "ready_to_send";
            case "SENT" -> "mark_sent";
            case "DELIVERED" -> "mark_delivered";
            default -> null;
        };
    }
}

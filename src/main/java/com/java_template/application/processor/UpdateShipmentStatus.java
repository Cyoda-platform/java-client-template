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
 * Processor for updating shipment status and deriving order status
 * Updates shipment quantities and synchronizes order status
 */
@Component
public class UpdateShipmentStatus implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateShipmentStatus.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UpdateShipmentStatus(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::updateShipmentStatusWithContext)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validate the entity wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Shipment> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or entity is null");
            return false;
        }

        Shipment shipment = entityWithMetadata.entity();
        if (!shipment.isValid()) {
            logger.error("Shipment entity is not valid: {}", shipment);
            return false;
        }

        return true;
    }

    /**
     * Update shipment status and derive order status
     */
    private EntityWithMetadata<Shipment> updateShipmentStatusWithContext(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Shipment> context) {
        EntityWithMetadata<Shipment> entityWithMetadata = context.entityResponse();
        Shipment shipment = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();
        
        logger.info("Updating shipment status: {} to state: {}", shipment.getShipmentId(), currentState);

        try {
            // Update shipment quantities based on status
            updateShipmentQuantities(shipment, currentState);
            
            // Update timestamp
            shipment.setUpdatedAt(LocalDateTime.now());

            // Update corresponding order status
            updateOrderStatus(shipment, currentState);

            logger.info("Updated shipment {} status to {}", shipment.getShipmentId(), currentState);
            
            return entityWithMetadata;

        } catch (Exception e) {
            logger.error("Error updating shipment status: {}", shipment.getShipmentId(), e);
            throw new RuntimeException("Failed to update shipment status", e);
        }
    }

    /**
     * Update shipment line quantities based on status
     */
    private void updateShipmentQuantities(Shipment shipment, String status) {
        if (shipment.getLines() == null) {
            return;
        }

        for (Shipment.ShipmentLine line : shipment.getLines()) {
            switch (status) {
                case "waiting_to_send":
                    // Mark all items as picked
                    line.setQtyPicked(line.getQtyOrdered());
                    break;
                case "sent":
                    // Mark all items as shipped
                    line.setQtyShipped(line.getQtyOrdered());
                    break;
                case "delivered":
                    // No quantity changes needed for delivered
                    break;
            }
        }
    }

    /**
     * Update corresponding order status based on shipment status
     */
    private void updateOrderStatus(Shipment shipment, String shipmentStatus) {
        logger.info("Updating order status for shipment: {} with status: {}", 
                   shipment.getShipmentId(), shipmentStatus);

        try {
            ModelSpec orderModelSpec = new ModelSpec();
            orderModelSpec.setName(Order.ENTITY_NAME);
            orderModelSpec.setVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                orderModelSpec, shipment.getOrderId(), "orderId", Order.class);

            if (orderWithMetadata != null) {
                Order order = orderWithMetadata.entity();
                String newOrderStatus = deriveOrderStatus(shipmentStatus);
                String orderTransition = getOrderTransition(shipmentStatus);
                
                if (newOrderStatus != null && !newOrderStatus.equals(order.getStatus())) {
                    order.setStatus(newOrderStatus);
                    order.setUpdatedAt(LocalDateTime.now());
                    
                    entityService.updateByBusinessId(order, "orderId", orderTransition);
                    
                    logger.info("Updated order {} status to {} via transition {}", 
                               order.getOrderId(), newOrderStatus, orderTransition);
                }
            } else {
                logger.warn("Order not found for shipment: {}", shipment.getShipmentId());
            }

        } catch (Exception e) {
            logger.error("Error updating order status for shipment: {}", shipment.getShipmentId(), e);
            // Don't throw exception here to avoid breaking shipment update
        }
    }

    /**
     * Derive order status from shipment status
     */
    private String deriveOrderStatus(String shipmentStatus) {
        switch (shipmentStatus) {
            case "picking":
                return "PICKING";
            case "waiting_to_send":
                return "WAITING_TO_SEND";
            case "sent":
                return "SENT";
            case "delivered":
                return "DELIVERED";
            default:
                return null;
        }
    }

    /**
     * Get order transition for shipment status
     */
    private String getOrderTransition(String shipmentStatus) {
        switch (shipmentStatus) {
            case "picking":
                return "start_picking";
            case "waiting_to_send":
                return "ready_to_send";
            case "sent":
                return "mark_sent";
            case "delivered":
                return "mark_delivered";
            default:
                return null;
        }
    }
}

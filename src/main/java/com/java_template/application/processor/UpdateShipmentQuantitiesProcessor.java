package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * ABOUTME: Processor for updating shipment quantities and synchronizing
 * order status based on shipment progress.
 */
@Component
public class UpdateShipmentQuantitiesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateShipmentQuantitiesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UpdateShipmentQuantitiesProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processShipmentQuantities)
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
     * Process shipment quantities update
     */
    private EntityWithMetadata<Shipment> processShipmentQuantities(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Shipment> context) {

        EntityWithMetadata<Shipment> entityWithMetadata = context.entityResponse();
        Shipment shipment = entityWithMetadata.entity();

        logger.debug("Processing shipment quantities for shipment: {}", shipment.getShipmentId());

        // Update quantities based on shipment status
        updateQuantitiesBasedOnStatus(shipment);

        // Update associated order status
        updateAssociatedOrderStatus(shipment);

        // Update timestamps
        shipment.setUpdatedAt(LocalDateTime.now());

        logger.info("Shipment quantities updated for shipment: {}", shipment.getShipmentId());

        return entityWithMetadata;
    }

    /**
     * Update quantities based on shipment status
     */
    private void updateQuantitiesBasedOnStatus(Shipment shipment) {
        String status = shipment.getStatus();
        
        for (Shipment.ShipmentLine line : shipment.getLines()) {
            switch (status) {
                case "WAITING_TO_SEND":
                    // When moving to WAITING_TO_SEND, assume all ordered quantities are picked
                    if (line.getQtyPicked() == null || line.getQtyPicked() == 0) {
                        line.setQtyPicked(line.getQtyOrdered());
                    }
                    break;
                case "SENT":
                    // When moving to SENT, assume all picked quantities are shipped
                    if (line.getQtyShipped() == null || line.getQtyShipped() == 0) {
                        line.setQtyShipped(line.getQtyPicked() != null ? line.getQtyPicked() : line.getQtyOrdered());
                    }
                    break;
            }
            
            logger.debug("Updated quantities for SKU {}: ordered={}, picked={}, shipped={}", 
                       line.getSku(), line.getQtyOrdered(), line.getQtyPicked(), line.getQtyShipped());
        }
    }

    /**
     * Update associated order status based on shipment status
     */
    private void updateAssociatedOrderStatus(Shipment shipment) {
        try {
            ModelSpec orderModelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    orderModelSpec, shipment.getOrderId(), "orderId", Order.class);
            
            if (orderWithMetadata != null) {
                Order order = orderWithMetadata.entity();
                String newOrderStatus = deriveOrderStatusFromShipment(shipment.getStatus());
                
                if (newOrderStatus != null && !newOrderStatus.equals(order.getStatus())) {
                    order.setStatus(newOrderStatus);
                    order.setUpdatedAt(LocalDateTime.now());
                    
                    String transition = getOrderTransitionForStatus(newOrderStatus);
                    entityService.update(orderWithMetadata.metadata().getId(), order, transition);
                    
                    logger.debug("Updated order {} status to: {}", shipment.getOrderId(), newOrderStatus);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to update associated order status for shipment: {}", shipment.getShipmentId(), e);
        }
    }

    /**
     * Derive order status from shipment status
     */
    private String deriveOrderStatusFromShipment(String shipmentStatus) {
        return switch (shipmentStatus) {
            case "PICKING" -> "PICKING";
            case "WAITING_TO_SEND" -> "WAITING_TO_SEND";
            case "SENT" -> "SENT";
            case "DELIVERED" -> "DELIVERED";
            default -> null;
        };
    }

    /**
     * Get order transition for status
     */
    private String getOrderTransitionForStatus(String status) {
        return switch (status) {
            case "PICKING" -> "start_picking";
            case "WAITING_TO_SEND" -> "ready_to_send";
            case "SENT" -> "mark_sent";
            case "DELIVERED" -> "mark_delivered";
            default -> null;
        };
    }
}

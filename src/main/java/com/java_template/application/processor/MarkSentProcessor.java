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
import java.util.UUID;

/**
 * ABOUTME: Processor for marking orders as sent, updating shipment status
 * and setting shipping timestamps and tracking information.
 */
@Component
public class MarkSentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkSentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public MarkSentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processMarkSent)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for Order
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for marking order as sent
     */
    private EntityWithMetadata<Order> processMarkSent(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing mark sent for orderId: {}", order.getOrderId());

        // Update order status
        order.setStatus("SENT");
        order.setUpdatedAt(LocalDateTime.now());

        // Update associated shipment status
        updateShipmentForSent(order.getOrderId());

        logger.info("Order {} marked as sent", order.getOrderId());

        return entityWithMetadata;
    }

    /**
     * Update shipment for sent status
     */
    private void updateShipmentForSent(String orderId) {
        try {
            ModelSpec shipmentModelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    shipmentModelSpec, orderId, "orderId", Shipment.class);

            if (shipmentWithMetadata != null) {
                Shipment shipment = shipmentWithMetadata.entity();
                shipment.setStatus("SENT");
                shipment.setShippedAt(LocalDateTime.now());
                shipment.setUpdatedAt(LocalDateTime.now());
                
                // Generate dummy tracking number
                shipment.setTrackingNumber("TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                shipment.setCarrier("DUMMY_CARRIER");

                // Mark all lines as fully shipped
                if (shipment.getLines() != null) {
                    for (Shipment.ShipmentLine line : shipment.getLines()) {
                        line.setQtyShipped(line.getQtyOrdered());
                    }
                }

                entityService.update(shipmentWithMetadata.metadata().getId(), shipment, null);
                logger.debug("Shipment {} marked as sent with tracking: {}", 
                           shipment.getShipmentId(), shipment.getTrackingNumber());
            } else {
                logger.warn("No shipment found for order: {}", orderId);
            }
        } catch (Exception e) {
            logger.error("Failed to update shipment for sent order: {}", orderId, e);
        }
    }
}

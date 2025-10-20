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
 * ABOUTME: Processor for marking orders as ready to send, updating shipment
 * status and completing the picking process.
 */
@Component
public class ReadyToSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReadyToSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ReadyToSendProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processReadyToSend)
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
     * Main business logic for ready to send processing
     */
    private EntityWithMetadata<Order> processReadyToSend(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing ready to send for orderId: {}", order.getOrderId());

        // Update order status
        order.setStatus("WAITING_TO_SEND");
        order.setUpdatedAt(LocalDateTime.now());

        // Update associated shipment status
        updateShipmentStatus(order.getOrderId(), "WAITING_TO_SEND");

        logger.info("Order {} marked as ready to send", order.getOrderId());

        return entityWithMetadata;
    }

    /**
     * Update shipment status for the order
     */
    private void updateShipmentStatus(String orderId, String newStatus) {
        try {
            ModelSpec shipmentModelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    shipmentModelSpec, orderId, "orderId", Shipment.class);

            if (shipmentWithMetadata != null) {
                Shipment shipment = shipmentWithMetadata.entity();
                shipment.setStatus(newStatus);
                shipment.setUpdatedAt(LocalDateTime.now());

                // Mark all lines as fully picked
                if (shipment.getLines() != null) {
                    for (Shipment.ShipmentLine line : shipment.getLines()) {
                        line.setQtyPicked(line.getQtyOrdered());
                    }
                }

                entityService.update(shipmentWithMetadata.metadata().getId(), shipment, null);
                logger.debug("Shipment {} status updated to {}", shipment.getShipmentId(), newStatus);
            } else {
                logger.warn("No shipment found for order: {}", orderId);
            }
        } catch (Exception e) {
            logger.error("Failed to update shipment status for order: {}", orderId, e);
        }
    }
}

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
 * ABOUTME: Processor for marking orders as delivered, completing the order
 * fulfillment lifecycle and updating shipment delivery status.
 */
@Component
public class MarkDeliveredProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkDeliveredProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public MarkDeliveredProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processMarkDelivered)
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
     * Main business logic for marking order as delivered
     */
    private EntityWithMetadata<Order> processMarkDelivered(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing mark delivered for orderId: {}", order.getOrderId());

        // Update order status
        order.setStatus("DELIVERED");
        order.setUpdatedAt(LocalDateTime.now());

        // Update associated shipment status
        updateShipmentForDelivered(order.getOrderId());

        logger.info("Order {} marked as delivered", order.getOrderId());

        return entityWithMetadata;
    }

    /**
     * Update shipment for delivered status
     */
    private void updateShipmentForDelivered(String orderId) {
        try {
            ModelSpec shipmentModelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    shipmentModelSpec, orderId, "orderId", Shipment.class);

            if (shipmentWithMetadata != null) {
                Shipment shipment = shipmentWithMetadata.entity();
                shipment.setStatus("DELIVERED");
                shipment.setDeliveredAt(LocalDateTime.now());
                shipment.setUpdatedAt(LocalDateTime.now());

                entityService.update(shipmentWithMetadata.metadata().getId(), shipment, null);
                logger.debug("Shipment {} marked as delivered", shipment.getShipmentId());
            } else {
                logger.warn("No shipment found for order: {}", orderId);
            }
        } catch (Exception e) {
            logger.error("Failed to update shipment for delivered order: {}", orderId, e);
        }
    }
}

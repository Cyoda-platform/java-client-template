package com.java_template.application.processor;

import com.java_template.application.entity.shipment.version_1.Shipment;
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
 * ABOUTME: Processor for confirming shipment delivery, completing the shipping
 * lifecycle and setting final delivery timestamps.
 */
@Component
public class ConfirmDeliveryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ConfirmDeliveryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ConfirmDeliveryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Shipment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid shipment entity wrapper")
                .map(this::processConfirmDelivery)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for Shipment
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Shipment> entityWithMetadata) {
        Shipment shipment = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return shipment != null && shipment.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for confirming delivery
     */
    private EntityWithMetadata<Shipment> processConfirmDelivery(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Shipment> context) {

        EntityWithMetadata<Shipment> entityWithMetadata = context.entityResponse();
        Shipment shipment = entityWithMetadata.entity();

        logger.debug("Processing confirm delivery for shipmentId: {}", shipment.getShipmentId());

        // Validate shipment is ready for delivery confirmation
        validateReadyForDelivery(shipment);

        // Set delivery status and timestamp
        shipment.setStatus("DELIVERED");
        shipment.setDeliveredAt(LocalDateTime.now());
        shipment.setUpdatedAt(LocalDateTime.now());

        logger.info("Delivery confirmed for shipment: {}, tracking: {}", 
                   shipment.getShipmentId(), shipment.getTrackingNumber());

        return entityWithMetadata;
    }

    /**
     * Validate shipment is ready for delivery confirmation
     */
    private void validateReadyForDelivery(Shipment shipment) {
        if (!"SENT".equals(shipment.getStatus())) {
            throw new IllegalStateException("Shipment must be SENT to confirm delivery, current status: " + shipment.getStatus());
        }

        if (shipment.getTrackingNumber() == null || shipment.getTrackingNumber().trim().isEmpty()) {
            throw new IllegalStateException("Shipment must have tracking number to confirm delivery: " + shipment.getShipmentId());
        }

        if (shipment.getShippedAt() == null) {
            throw new IllegalStateException("Shipment must have shipped date to confirm delivery: " + shipment.getShipmentId());
        }

        logger.debug("Shipment validation passed for delivery confirmation: {}", shipment.getShipmentId());
    }
}

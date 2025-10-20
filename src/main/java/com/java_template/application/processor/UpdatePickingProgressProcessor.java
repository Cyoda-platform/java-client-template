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
 * ABOUTME: Processor for updating picking progress on shipments, tracking
 * picked quantities and validating picking constraints.
 */
@Component
public class UpdatePickingProgressProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdatePickingProgressProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdatePickingProgressProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Shipment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid shipment entity wrapper")
                .map(this::processPickingProgress)
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
     * Main business logic for picking progress updates
     */
    private EntityWithMetadata<Shipment> processPickingProgress(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Shipment> context) {

        EntityWithMetadata<Shipment> entityWithMetadata = context.entityResponse();
        Shipment shipment = entityWithMetadata.entity();

        logger.debug("Processing picking progress for shipmentId: {}", shipment.getShipmentId());

        // Validate picking progress
        validatePickingProgress(shipment);

        // Update timestamp
        shipment.setUpdatedAt(LocalDateTime.now());

        // Log picking status
        logPickingStatus(shipment);

        logger.info("Picking progress updated for shipment: {}", shipment.getShipmentId());

        return entityWithMetadata;
    }

    /**
     * Validate picking progress constraints
     */
    private void validatePickingProgress(Shipment shipment) {
        if (shipment.getLines() == null || shipment.getLines().isEmpty()) {
            throw new IllegalStateException("Shipment must have lines to update picking progress: " + shipment.getShipmentId());
        }

        for (Shipment.ShipmentLine line : shipment.getLines()) {
            // Validate picked quantity doesn't exceed ordered quantity
            if (line.getQtyPicked() != null && line.getQtyOrdered() != null) {
                if (line.getQtyPicked() > line.getQtyOrdered()) {
                    throw new IllegalStateException(
                        String.format("Picked quantity (%d) cannot exceed ordered quantity (%d) for SKU %s in shipment %s",
                                    line.getQtyPicked(), line.getQtyOrdered(), line.getSku(), shipment.getShipmentId()));
                }
            }

            // Ensure picked quantity is not negative
            if (line.getQtyPicked() != null && line.getQtyPicked() < 0) {
                throw new IllegalStateException(
                    String.format("Picked quantity cannot be negative for SKU %s in shipment %s",
                                line.getSku(), shipment.getShipmentId()));
            }
        }

        logger.debug("Picking progress validation passed for shipment: {}", shipment.getShipmentId());
    }

    /**
     * Log current picking status
     */
    private void logPickingStatus(Shipment shipment) {
        if (shipment.getLines() == null) {
            return;
        }

        int totalOrdered = 0;
        int totalPicked = 0;

        for (Shipment.ShipmentLine line : shipment.getLines()) {
            if (line.getQtyOrdered() != null) {
                totalOrdered += line.getQtyOrdered();
            }
            if (line.getQtyPicked() != null) {
                totalPicked += line.getQtyPicked();
            }
        }

        double pickingProgress = totalOrdered > 0 ? (double) totalPicked / totalOrdered * 100 : 0;

        logger.debug("Picking status for shipment {}: {}/{} items picked ({:.1f}%)",
                    shipment.getShipmentId(), totalPicked, totalOrdered, pickingProgress);
    }
}

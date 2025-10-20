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
import java.util.UUID;

/**
 * ABOUTME: Processor for shipping packages, generating tracking numbers,
 * setting carrier information, and updating shipped quantities.
 */
@Component
public class ShipPackageProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipPackageProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ShipPackageProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Shipment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid shipment entity wrapper")
                .map(this::processShipPackage)
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
     * Main business logic for shipping packages
     */
    private EntityWithMetadata<Shipment> processShipPackage(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Shipment> context) {

        EntityWithMetadata<Shipment> entityWithMetadata = context.entityResponse();
        Shipment shipment = entityWithMetadata.entity();

        logger.debug("Processing ship package for shipmentId: {}", shipment.getShipmentId());

        // Validate shipment is ready to ship
        validateReadyToShip(shipment);

        // Generate tracking information
        generateTrackingInfo(shipment);

        // Update shipped quantities
        updateShippedQuantities(shipment);

        // Set shipment status and timestamps
        shipment.setStatus("SENT");
        shipment.setShippedAt(LocalDateTime.now());
        shipment.setUpdatedAt(LocalDateTime.now());

        logger.info("Package shipped for shipment: {}, tracking: {}", 
                   shipment.getShipmentId(), shipment.getTrackingNumber());

        return entityWithMetadata;
    }

    /**
     * Validate shipment is ready to ship
     */
    private void validateReadyToShip(Shipment shipment) {
        if (!"WAITING_TO_SEND".equals(shipment.getStatus())) {
            throw new IllegalStateException("Shipment must be WAITING_TO_SEND to ship, current status: " + shipment.getStatus());
        }

        if (shipment.getLines() == null || shipment.getLines().isEmpty()) {
            throw new IllegalStateException("Shipment must have lines to ship: " + shipment.getShipmentId());
        }

        // Validate all items are picked
        for (Shipment.ShipmentLine line : shipment.getLines()) {
            if (line.getQtyPicked() == null || line.getQtyOrdered() == null) {
                throw new IllegalStateException(
                    String.format("Missing quantity data for SKU %s in shipment %s", 
                                line.getSku(), shipment.getShipmentId()));
            }

            if (line.getQtyPicked() < line.getQtyOrdered()) {
                throw new IllegalStateException(
                    String.format("Not all items picked for SKU %s in shipment %s (picked: %d, ordered: %d)",
                                line.getSku(), shipment.getShipmentId(), line.getQtyPicked(), line.getQtyOrdered()));
            }
        }

        logger.debug("Shipment validation passed for shipping: {}", shipment.getShipmentId());
    }

    /**
     * Generate tracking information
     */
    private void generateTrackingInfo(Shipment shipment) {
        // Generate dummy tracking number
        String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        shipment.setTrackingNumber(trackingNumber);
        
        // Set dummy carrier
        shipment.setCarrier("DUMMY_EXPRESS");

        logger.debug("Generated tracking info for shipment {}: {} via {}", 
                    shipment.getShipmentId(), trackingNumber, shipment.getCarrier());
    }

    /**
     * Update shipped quantities to match picked quantities
     */
    private void updateShippedQuantities(Shipment shipment) {
        if (shipment.getLines() == null) {
            return;
        }

        for (Shipment.ShipmentLine line : shipment.getLines()) {
            // Set shipped quantity to picked quantity
            line.setQtyShipped(line.getQtyPicked());
            
            logger.debug("Updated shipped quantity for SKU {}: {}", 
                        line.getSku(), line.getQtyShipped());
        }
    }
}

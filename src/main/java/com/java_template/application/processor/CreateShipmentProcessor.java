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
 * ABOUTME: Processor for creating new Shipment entities, initializing shipment state
 * and setting up shipment lines for picking operations.
 */
@Component
public class CreateShipmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateShipmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateShipmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Shipment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid shipment entity wrapper")
                .map(this::processShipmentCreation)
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
     * Main business logic for shipment creation
     */
    private EntityWithMetadata<Shipment> processShipmentCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Shipment> context) {

        EntityWithMetadata<Shipment> entityWithMetadata = context.entityResponse();
        Shipment shipment = entityWithMetadata.entity();

        logger.debug("Processing shipment creation for shipmentId: {}", shipment.getShipmentId());

        // Initialize shipment state
        initializeShipment(shipment);

        logger.info("Shipment {} created successfully for order {}", 
                   shipment.getShipmentId(), shipment.getOrderId());

        return entityWithMetadata;
    }

    /**
     * Initialize shipment with default values
     */
    private void initializeShipment(Shipment shipment) {
        // Set status to PICKING
        shipment.setStatus("PICKING");
        
        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        if (shipment.getCreatedAt() == null) {
            shipment.setCreatedAt(now);
        }
        shipment.setUpdatedAt(now);

        // Initialize picking quantities to 0 if not set
        if (shipment.getLines() != null) {
            for (Shipment.ShipmentLine line : shipment.getLines()) {
                if (line.getQtyPicked() == null) {
                    line.setQtyPicked(0);
                }
                if (line.getQtyShipped() == null) {
                    line.setQtyShipped(0);
                }
            }
        }
        
        logger.debug("Shipment initialized with status PICKING for shipmentId: {}", shipment.getShipmentId());
    }
}

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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * ShipmentCreateProcessor - Initializes a new shipment
 * 
 * This processor handles:
 * - Setting initial shipment status to PICKING
 * - Initializing shipment lines with zero picked/shipped quantities
 * - Setting creation timestamp
 * 
 * Triggered by: CREATE_SHIPMENT transition
 */
@Component
public class ShipmentCreateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentCreateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ShipmentCreateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing shipment creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Shipment.class)
            .validate(this::isValidEntityWithMetadata, "Invalid shipment entity")
            .map(this::processShipmentCreationLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the Shipment EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Shipment> entityWithMetadata) {
        Shipment shipment = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return shipment != null && technicalId != null;
    }

    /**
     * Main business logic for shipment creation
     */
    private EntityWithMetadata<Shipment> processShipmentCreationLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Shipment> context) {
        
        EntityWithMetadata<Shipment> entityWithMetadata = context.entityResponse();
        Shipment shipment = entityWithMetadata.entity();

        logger.debug("Initializing new shipment: {} for order: {}", 
                    shipment.getShipmentId(), shipment.getOrderId());

        // Initialize shipment lines with zero picked/shipped quantities
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

        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        if (shipment.getCreatedAt() == null) {
            shipment.setCreatedAt(now);
        }
        shipment.setUpdatedAt(now);

        logger.info("Shipment {} initialized successfully for order: {}", 
                   shipment.getShipmentId(), shipment.getOrderId());

        return entityWithMetadata;
    }
}

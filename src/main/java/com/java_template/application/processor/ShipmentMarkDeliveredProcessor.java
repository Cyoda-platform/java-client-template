package com.java_template.application.processor;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class ShipmentMarkDeliveredProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentMarkDeliveredProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ShipmentMarkDeliveredProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment mark delivered for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract shipment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract shipment entity: " + error.getMessage());
            })
            .validate(this::isValidShipment, "Invalid shipment state")
            .map(this::processShipmentMarkDelivered)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidShipment(Shipment shipment) {
        return shipment != null && 
               shipment.getShipmentId() != null && !shipment.getShipmentId().trim().isEmpty() &&
               shipment.getOrderId() != null && !shipment.getOrderId().trim().isEmpty();
    }

    private Shipment processShipmentMarkDelivered(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();
        
        logger.info("Processing shipment mark delivered: {}", shipment.getShipmentId());
        
        try {
            // CRITICAL: The shipment entity already contains all the data we need
            // Never extract from request payload - use shipment getters directly
            
            // Update shipment timestamp
            shipment.setUpdatedAt(Instant.now());
            
            // Log shipment completion
            logger.info("Shipment {} marked as delivered and completed for order {}", 
                shipment.getShipmentId(), shipment.getOrderId());
            
            return shipment;
            
        } catch (Exception e) {
            logger.error("Failed to process shipment mark delivered: {}", e.getMessage(), e);
            throw new RuntimeException("Shipment mark delivered processing failed: " + e.getMessage(), e);
        }
    }
}

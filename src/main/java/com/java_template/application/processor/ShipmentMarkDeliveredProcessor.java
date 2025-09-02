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
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Shipment entity) {
        return entity != null && entity.isValid();
    }

    private Shipment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();

        try {
            // Update timestamp - delivery finalizes the shipment lifecycle
            shipment.setUpdatedAt(Instant.now());

            logger.info("Shipment {} marked as delivered - lifecycle complete", shipment.getShipmentId());

            return shipment;

        } catch (Exception e) {
            logger.error("Error processing shipment mark delivered: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process shipment mark delivered: " + e.getMessage(), e);
        }
    }
}
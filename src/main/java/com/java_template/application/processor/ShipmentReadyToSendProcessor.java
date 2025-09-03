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
public class ShipmentReadyToSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentReadyToSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ShipmentReadyToSendProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment ready to send for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract shipment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract shipment entity: " + error.getMessage());
            })
            .validate(this::isValidShipment, "Invalid shipment state")
            .map(this::processShipmentReadyToSend)
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

    private Shipment processShipmentReadyToSend(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();
        
        logger.info("Processing shipment ready to send: {}", shipment.getShipmentId());
        
        try {
            // CRITICAL: The shipment entity already contains all the data we need
            // Never extract from request payload - use shipment getters directly
            
            // Mark all lines as picked (qtyPicked = qtyOrdered)
            if (shipment.getLines() != null) {
                for (Shipment.ShipmentLine line : shipment.getLines()) {
                    if (line.getQtyOrdered() != null) {
                        line.setQtyPicked(line.getQtyOrdered());
                        logger.debug("Marked line {} as picked: {} units", line.getSku(), line.getQtyPicked());
                    }
                }
            }
            
            // Update shipment timestamp
            shipment.setUpdatedAt(Instant.now());
            
            logger.info("Shipment {} marked as ready to send, all items picked", shipment.getShipmentId());
            
            return shipment;
            
        } catch (Exception e) {
            logger.error("Failed to process shipment ready to send: {}", e.getMessage(), e);
            throw new RuntimeException("Shipment ready to send processing failed: " + e.getMessage(), e);
        }
    }
}

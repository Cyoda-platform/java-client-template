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
public class ShipmentMarkSentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentMarkSentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ShipmentMarkSentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment mark sent for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract shipment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract shipment entity: " + error.getMessage());
            })
            .validate(this::isValidShipment, "Invalid shipment state")
            .map(this::processShipmentMarkSent)
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

    private Shipment processShipmentMarkSent(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();
        
        logger.info("Processing shipment mark sent: {}", shipment.getShipmentId());
        
        try {
            // CRITICAL: The shipment entity already contains all the data we need
            // Never extract from request payload - use shipment getters directly
            
            // Mark all lines as shipped (qtyShipped = qtyPicked)
            if (shipment.getLines() != null) {
                for (Shipment.ShipmentLine line : shipment.getLines()) {
                    if (line.getQtyPicked() != null) {
                        line.setQtyShipped(line.getQtyPicked());
                        logger.debug("Marked line {} as shipped: {} units", line.getSku(), line.getQtyShipped());
                    }
                }
            }
            
            // Update shipment timestamp
            shipment.setUpdatedAt(Instant.now());
            
            logger.info("Shipment {} marked as sent, all items shipped", shipment.getShipmentId());
            
            return shipment;
            
        } catch (Exception e) {
            logger.error("Failed to process shipment mark sent: {}", e.getMessage(), e);
            throw new RuntimeException("Shipment mark sent processing failed: " + e.getMessage(), e);
        }
    }
}

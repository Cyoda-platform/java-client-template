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
        logger.info("Processing Shipment create for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract shipment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract shipment entity: " + error.getMessage());
            })
            .validate(this::isValidShipment, "Invalid shipment state")
            .map(this::createShipment)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidShipment(Shipment shipment) {
        return shipment != null && 
               shipment.getOrderId() != null && !shipment.getOrderId().trim().isEmpty() &&
               shipment.getLines() != null && !shipment.getLines().isEmpty();
    }

    private Shipment createShipment(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();

        logger.info("Creating shipment for order: {}", shipment.getOrderId());

        // Initialize shipment lines for picking
        for (Shipment.ShipmentLine line : shipment.getLines()) {
            if (line.getQtyOrdered() == null || line.getQtyOrdered() <= 0) {
                throw new IllegalStateException("Invalid quantity ordered for SKU: " + line.getSku());
            }
            
            // Initialize picking quantities
            line.setQtyPicked(0);
            line.setQtyShipped(0);
        }

        // Set timestamps
        Instant now = Instant.now();
        shipment.setCreatedAt(now);
        shipment.setUpdatedAt(now);

        logger.info("Shipment created for order: {}", shipment.getOrderId());

        return shipment;
    }
}

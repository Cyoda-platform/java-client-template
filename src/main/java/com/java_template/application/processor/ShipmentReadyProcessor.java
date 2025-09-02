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
public class ShipmentReadyProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentReadyProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ShipmentReadyProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment ready for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidShipment, "Invalid shipment state")
            .map(this::processShipmentReady)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidShipment(Shipment shipment) {
        return shipment != null && shipment.isValid();
    }

    private Shipment processShipmentReady(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();

        // Validate shipment is in PICKING state
        String currentState = context.request().getPayload().getMeta().get("state").toString();
        if (!"PICKING".equals(currentState)) {
            throw new IllegalStateException("Shipment must be in PICKING state to mark ready");
        }

        // Process each shipment line
        for (Shipment.ShipmentLine line : shipment.getLines()) {
            // If qtyPicked is 0, set it to qtyOrdered (assume full pick)
            if (line.getQtyPicked() == null || line.getQtyPicked() == 0) {
                line.setQtyPicked(line.getQtyOrdered());
            }

            // Validate qtyPicked <= qtyOrdered
            if (line.getQtyPicked() > line.getQtyOrdered()) {
                throw new IllegalArgumentException("Picked quantity cannot exceed ordered quantity for SKU: " + line.getSku());
            }
        }

        // Set updatedAt timestamp
        shipment.setUpdatedAt(Instant.now());

        logger.info("Shipment {} ready to send", shipment.getShipmentId());
        return shipment;
    }
}

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
public class ShipmentAdvanceProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentAdvanceProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ShipmentAdvanceProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment advance for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract shipment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract shipment entity: " + error.getMessage());
            })
            .validate(this::isValidShipmentForAdvance, "Invalid shipment state for advance")
            .map(this::processShipmentAdvance)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidShipmentForAdvance(Shipment shipment) {
        return shipment != null &&
               shipment.getShipmentId() != null &&
               shipment.getStatus() != null;
    }

    private Shipment processShipmentAdvance(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();
        String currentStatus = shipment.getStatus();

        // Advance shipment status based on current state
        switch (currentStatus) {
            case "PICKING":
                shipment.setStatus("WAITING_TO_SEND");
                updateShipmentLines(shipment, "WAITING_TO_SEND");
                logger.info("Advanced shipment {} from PICKING to WAITING_TO_SEND", shipment.getShipmentId());
                break;

            case "WAITING_TO_SEND":
                shipment.setStatus("SENT");
                updateShipmentLines(shipment, "SENT");
                logger.info("Advanced shipment {} from WAITING_TO_SEND to SENT", shipment.getShipmentId());
                break;

            case "SENT":
                shipment.setStatus("DELIVERED");
                updateShipmentLines(shipment, "DELIVERED");
                logger.info("Advanced shipment {} from SENT to DELIVERED", shipment.getShipmentId());
                break;

            default:
                logger.warn("Cannot advance shipment {} from status {}", shipment.getShipmentId(), currentStatus);
                break;
        }

        shipment.setUpdatedAt(Instant.now().toString());

        return shipment;
    }

    private void updateShipmentLines(Shipment shipment, String newStatus) {
        if (shipment.getLines() != null) {
            for (Shipment.Line line : shipment.getLines()) {
                switch (newStatus) {
                    case "WAITING_TO_SEND":
                        // Mark all ordered quantities as picked
                        if (line.getQtyOrdered() != null) {
                            line.setQtyPicked(line.getQtyOrdered());
                        }
                        break;

                    case "SENT":
                        // Mark all picked quantities as shipped
                        if (line.getQtyPicked() != null) {
                            line.setQtyShipped(line.getQtyPicked());
                        }
                        break;

                    case "DELIVERED":
                        // No additional updates needed for delivery
                        break;
                }
            }
        }
    }
}
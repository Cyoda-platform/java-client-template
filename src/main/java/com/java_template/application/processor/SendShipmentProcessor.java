package com.java_template.application.processor;
import com.java_template.application.entity.shipment.version_1.Shipment;
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

import java.time.Instant;
import java.util.List;

@Component
public class SendShipmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendShipmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendShipmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Shipment.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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
        Shipment entity = context.entity();

        // Business rule:
        // - This processor transitions a Shipment from WAITING_TO_SEND -> SENT.
        // - It must assign a carrier and tracking number if not present (dummy values allowed).
        // - It must set qtyShipped on each shipment line to the qtyPicked (or qtyOrdered if qtyPicked is null).
        // - It must update the shipment.updatedAt timestamp.
        // - If the shipment is not in WAITING_TO_SEND state, do nothing.

        if (entity == null) {
            logger.warn("Shipment entity is null in context");
            return entity;
        }

        String currentStatus = entity.getStatus();
        if (currentStatus == null) {
            logger.warn("Shipment {} has null status, skipping send", entity.getShipmentId());
            return entity;
        }

        // Only proceed if shipment is in WAITING_TO_SEND (case-sensitive match per domain strings)
        if (!"WAITING_TO_SEND".equals(currentStatus)) {
            logger.info("Shipment {} is in status '{}' (not WAITING_TO_SEND) - no action taken", entity.getShipmentId(), currentStatus);
            return entity;
        }

        // Assign dummy carrier if missing
        if (entity.getCarrier() == null || entity.getCarrier().isBlank()) {
            entity.setCarrier("DUMMY");
        }

        // Assign tracking number if missing
        if (entity.getTrackingNumber() == null || entity.getTrackingNumber().isBlank()) {
            String trk = "TRK-" + (entity.getShipmentId() != null ? entity.getShipmentId() : Instant.now().toEpochMilli());
            entity.setTrackingNumber(trk);
        }

        // Set qtyShipped from qtyPicked (or qtyOrdered fallback)
        List<Shipment.Line> lines = entity.getLines();
        if (lines != null) {
            for (Shipment.Line line : lines) {
                if (line == null) continue;
                Integer qtyPicked = line.getQtyPicked();
                Integer qtyOrdered = line.getQtyOrdered();
                if (qtyPicked != null) {
                    line.setQtyShipped(qtyPicked);
                } else if (qtyOrdered != null) {
                    line.setQtyShipped(qtyOrdered);
                } else {
                    // explicit fallback to 0 if neither present
                    line.setQtyShipped(0);
                }
            }
        }

        // Transition status to SENT
        entity.setStatus("SENT");

        // Update timestamp
        entity.setUpdatedAt(Instant.now().toString());

        logger.info("Shipment {} marked as SENT with carrier {} and tracking {}", entity.getShipmentId(), entity.getCarrier(), entity.getTrackingNumber());

        return entity;
    }
}
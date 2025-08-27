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
import java.util.UUID;

@Component
public class AssignCarrierProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AssignCarrierProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AssignCarrierProcessor(SerializerFactory serializerFactory) {
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
        Shipment shipment = context.entity();

        // Business rule:
        // - Assign carrier and tracking number when shipment is WAITING_TO_SEND and all lines are fully picked
        // - Do not modify if shipment is not in WAITING_TO_SEND
        if (shipment == null) {
            logger.warn("Shipment is null in context, nothing to do.");
            return shipment;
        }

        String currentStatus = shipment.getStatus();
        if (currentStatus == null) {
            logger.warn("Shipment {} has null status, skipping carrier assignment.", shipment.getShipmentId());
            return shipment;
        }

        if (!"WAITING_TO_SEND".equalsIgnoreCase(currentStatus)) {
            logger.info("Shipment {} status is '{}' (not WAITING_TO_SEND). Skipping carrier assignment.", shipment.getShipmentId(), currentStatus);
            return shipment;
        }

        List<Shipment.Line> lines = shipment.getLines();
        if (lines == null || lines.isEmpty()) {
            logger.warn("Shipment {} has no lines, cannot assign carrier.", shipment.getShipmentId());
            return shipment;
        }

        // Verify all lines are fully picked (qtyPicked >= qtyOrdered)
        for (Shipment.Line line : lines) {
            Integer qtyOrdered = line.getQtyOrdered();
            Integer qtyPicked = line.getQtyPicked();
            if (qtyOrdered == null) {
                logger.warn("Shipment {} line with sku '{}' has null qtyOrdered, cannot assign carrier.", shipment.getShipmentId(), line.getSku());
                return shipment;
            }
            if (qtyPicked == null || qtyPicked.intValue() < qtyOrdered.intValue()) {
                logger.info("Shipment {} not fully picked for sku '{}': picked={} ordered={}. Deferring carrier assignment.",
                        shipment.getShipmentId(), line.getSku(), qtyPicked, qtyOrdered);
                return shipment;
            }
        }

        // All lines fully picked -> assign carrier and tracking, update status to SENT
        String carrier = shipment.getCarrier();
        if (carrier == null || carrier.isBlank()) {
            carrier = "DUMMY_CARRIER";
            shipment.setCarrier(carrier);
        }

        String tracking = shipment.getTrackingNumber();
        if (tracking == null || tracking.isBlank()) {
            String base = shipment.getShipmentId() != null && !shipment.getShipmentId().isBlank()
                    ? shipment.getShipmentId()
                    : UUID.randomUUID().toString();
            // create a simple tracking id using a prefix and short suffix of the id
            String suffix = base.length() > 8 ? base.substring(base.length() - 8) : base;
            tracking = "TRK-" + suffix;
            shipment.setTrackingNumber(tracking);
        }

        shipment.setStatus("SENT");
        shipment.setUpdatedAt(Instant.now().toString());

        logger.info("Assigned carrier '{}' and tracking '{}' to shipment {} and set status to SENT.",
                shipment.getCarrier(), shipment.getTrackingNumber(), shipment.getShipmentId());

        return shipment;
    }
}
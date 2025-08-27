package com.java_template.application.processor;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class AutoMarkDeliveredAfterDelayProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoMarkDeliveredAfterDelayProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AutoMarkDeliveredAfterDelayProcessor(SerializerFactory serializerFactory) {
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

        // Business logic:
        // If shipment is currently SENT, mark it as DELIVERED.
        // Also set updatedAt timestamp and ensure lines have qtyShipped set (use qtyPicked where available).
        String status = entity.getStatus();
        if (status != null && status.equalsIgnoreCase("SENT")) {
            entity.setStatus("DELIVERED");
            entity.setUpdatedAt(Instant.now().toString());

            List<Shipment.Line> lines = entity.getLines();
            if (lines != null) {
                for (Shipment.Line line : lines) {
                    if (line == null) continue;
                    Integer qtyPicked = line.getQtyPicked();
                    // If qtyPicked is present, set qtyShipped to that value.
                    if (qtyPicked != null) {
                        line.setQtyShipped(qtyPicked);
                    } else {
                        // If no qtyPicked and qtyShipped is null, set to 0 to indicate nothing shipped.
                        if (line.getQtyShipped() == null) {
                            line.setQtyShipped(0);
                        }
                    }
                }
            }

            logger.info("Shipment {} auto-marked as DELIVERED by {}", entity.getShipmentId(), className);
        } else {
            logger.info("AutoMarkDeliveredAfterDelayProcessor skipped for shipment {} with status '{}'", entity.getShipmentId(), status);
        }

        return entity;
    }
}
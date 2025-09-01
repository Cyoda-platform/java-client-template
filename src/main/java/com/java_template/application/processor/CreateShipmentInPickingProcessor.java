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
import java.util.UUID;

@Component
public class CreateShipmentInPickingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateShipmentInPickingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateShipmentInPickingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment creation in picking for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract shipment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract shipment entity: " + error.getMessage());
            })
            .validate(this::isValidShipmentForCreation, "Invalid shipment state for creation")
            .map(this::processShipmentCreation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidShipmentForCreation(Shipment shipment) {
        return shipment != null &&
               shipment.getOrderId() != null &&
               shipment.getLines() != null &&
               !shipment.getLines().isEmpty();
    }

    private Shipment processShipmentCreation(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();

        // Set shipment ID if not present
        if (shipment.getShipmentId() == null || shipment.getShipmentId().isBlank()) {
            shipment.setShipmentId("SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        // Set status to PICKING
        shipment.setStatus("PICKING");

        // Set timestamps
        String now = Instant.now().toString();
        shipment.setCreatedAt(now);
        shipment.setUpdatedAt(now);

        // Initialize shipment lines with picking quantities
        if (shipment.getLines() != null) {
            for (Shipment.Line line : shipment.getLines()) {
                if (line.getQtyPicked() == null) {
                    line.setQtyPicked(0);
                }
                if (line.getQtyShipped() == null) {
                    line.setQtyShipped(0);
                }
            }
        }

        logger.info("Created shipment {} for order {} with status PICKING",
                   shipment.getShipmentId(), shipment.getOrderId());

        return shipment;
    }
}
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

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class ShipmentDeliverProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentDeliverProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public ShipmentDeliverProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment deliver for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid shipment state")
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
        
        logger.info("Marking shipment as delivered: {}", shipment.getShipmentId());

        // Validate shipment entity
        if (shipment == null) {
            logger.error("Shipment entity is null");
            throw new IllegalArgumentException("Shipment entity cannot be null");
        }

        if (shipment.getShipmentId() == null || shipment.getShipmentId().trim().isEmpty()) {
            logger.error("Shipment ID is required");
            throw new IllegalArgumentException("Shipment ID is required");
        }

        if (shipment.getLines() == null || shipment.getLines().isEmpty()) {
            logger.error("Shipment must have line items");
            throw new IllegalArgumentException("Shipment must have line items");
        }

        // Validate all items are shipped before delivery
        for (Shipment.ShipmentLine line : shipment.getLines()) {
            if (line.getQtyShipped() == null || line.getQtyShipped() <= 0) {
                logger.error("All items must be shipped before delivery. SKU: {} has shipped qty: {}", 
                           line.getSku(), line.getQtyShipped());
                throw new IllegalStateException("All items must be shipped before delivery");
            }
            
            if (!line.getQtyShipped().equals(line.getQtyOrdered())) {
                logger.error("Shipped quantity must equal ordered quantity for delivery. SKU: {} - shipped: {}, ordered: {}", 
                           line.getSku(), line.getQtyShipped(), line.getQtyOrdered());
                throw new IllegalStateException("Shipped quantity must equal ordered quantity for delivery");
            }
        }

        // Update shipment timestamp
        shipment.setUpdatedAt(LocalDateTime.now());

        logger.info("Shipment marked as delivered successfully: {} - all items delivered", shipment.getShipmentId());

        return shipment;
    }
}

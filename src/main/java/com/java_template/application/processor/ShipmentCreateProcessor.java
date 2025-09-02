package com.java_template.application.processor;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ShipmentCreateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentCreateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ShipmentCreateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
            .map(this::processCreateShipment)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidShipment(Shipment shipment) {
        return shipment != null && shipment.isValid();
    }

    private Shipment processCreateShipment(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();
        
        // Extract order ID from context
        String orderId = extractOrderIdFromContext(context);
        
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID is required for shipment creation");
        }
        
        logger.info("Creating shipment for order: {}", orderId);
        
        // Validate order exists and is being created
        Order order = getOrderById(orderId);
        if (order == null) {
            throw new IllegalStateException("Order not found: " + orderId);
        }
        
        // Copy order lines to shipment lines
        List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
        for (Order.OrderLine orderLine : order.getLines()) {
            Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
            shipmentLine.setSku(orderLine.getSku());
            shipmentLine.setQtyOrdered(orderLine.getQty());
            shipmentLine.setQtyPicked(0);
            shipmentLine.setQtyShipped(0);
            shipmentLines.add(shipmentLine);
        }
        
        shipment.setLines(shipmentLines);
        shipment.setOrderId(orderId);
        
        // Update timestamps
        shipment.setUpdatedAt(Instant.now());
        
        logger.info("Shipment created successfully: {}", shipment.getShipmentId());
        return shipment;
    }

    private String extractOrderIdFromContext(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        // TODO: Extract from context - placeholder implementation
        return null;
    }

    private Order getOrderById(String orderId) {
        // TODO: Implement proper order lookup
        return null;
    }
}

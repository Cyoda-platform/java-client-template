package com.java_template.application.processor;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.order.version_1.Order;
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
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ShipmentCreateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentCreateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

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
        
        logger.info("Creating shipment for order: {}", shipment.getOrderId());

        // Validate shipment entity
        if (shipment == null) {
            logger.error("Shipment entity is null");
            throw new IllegalArgumentException("Shipment entity cannot be null");
        }

        if (shipment.getOrderId() == null || shipment.getOrderId().trim().isEmpty()) {
            logger.error("Order ID is required for shipment creation");
            throw new IllegalArgumentException("Order ID is required");
        }

        // Generate unique shipment ID if not set
        if (shipment.getShipmentId() == null || shipment.getShipmentId().trim().isEmpty()) {
            String shipmentId = "ship_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            shipment.setShipmentId(shipmentId);
            logger.info("Generated shipment ID: {}", shipmentId);
        }

        // Get order details to create shipment lines
        Order order = getOrderDetails(shipment.getOrderId());
        
        // Create shipment lines from order lines
        List<Shipment.ShipmentLine> shipmentLines = createShipmentLines(order);
        shipment.setLines(shipmentLines);

        // Set timestamps
        if (shipment.getCreatedAt() == null) {
            shipment.setCreatedAt(LocalDateTime.now());
        }
        shipment.setUpdatedAt(LocalDateTime.now());

        logger.info("Shipment created successfully: {} for order: {} with {} lines", 
                   shipment.getShipmentId(), shipment.getOrderId(), shipmentLines.size());

        return shipment;
    }

    /**
     * Get order details by order ID.
     */
    private Order getOrderDetails(String orderId) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("orderId", "equals", orderId));
            
            var orderResponse = entityService.getFirstItemByCondition(Order.class, condition, false);
            
            if (orderResponse.isPresent()) {
                return orderResponse.get().getData();
            } else {
                logger.error("Order not found: {}", orderId);
                throw new IllegalStateException("Order not found: " + orderId);
            }
            
        } catch (Exception e) {
            logger.error("Error getting order details: {}", orderId, e);
            throw new RuntimeException("Failed to get order details", e);
        }
    }

    /**
     * Create shipment lines from order lines.
     */
    private List<Shipment.ShipmentLine> createShipmentLines(Order order) {
        List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
        
        if (order.getLines() != null) {
            shipmentLines = order.getLines().stream()
                .map(orderLine -> {
                    Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                    shipmentLine.setSku(orderLine.getSku());
                    shipmentLine.setQtyOrdered(orderLine.getQty());
                    shipmentLine.setQtyPicked(0); // Initially 0
                    shipmentLine.setQtyShipped(0); // Initially 0
                    
                    logger.debug("Created shipment line for SKU: {} with ordered qty: {}", 
                               orderLine.getSku(), orderLine.getQty());
                    
                    return shipmentLine;
                })
                .collect(Collectors.toList());
        }
        
        return shipmentLines;
    }
}

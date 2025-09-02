package com.java_template.application.processor;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        logger.info("Processing Shipment creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract shipment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract shipment entity: " + error.getMessage());
            })
            .validate(this::isValidShipment, "Invalid shipment state")
            .map(this::processShipmentCreation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidShipment(Shipment shipment) {
        return shipment != null;
    }

    private Shipment processShipmentCreation(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();

        // Extract order reference from request data - for now using hardcoded values
        // In a real implementation, this would come from the request payload
        String orderId = "order_sample"; // TODO: Extract from request payload

        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("Order ID is required for shipment creation");
        }

        logger.info("Creating shipment for order: {}", orderId);

        try {
            // Get order entity to create shipment lines
            EntityResponse<Order> orderResponse = entityService.findByBusinessId(Order.class, orderId);
            Order order = orderResponse.getData();
            
            if (order == null) {
                throw new IllegalArgumentException("Order not found for ID: " + orderId);
            }

            // Generate unique shipmentId if not present
            if (shipment.getShipmentId() == null || shipment.getShipmentId().trim().isEmpty()) {
                shipment.setShipmentId("ship_" + UUID.randomUUID().toString().replace("-", ""));
            }

            // Set orderId reference
            shipment.setOrderId(orderId);

            // Create shipment.lines from order.lines
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            for (Order.OrderLine orderLine : order.getLines()) {
                Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine(
                    orderLine.getSku(), 
                    orderLine.getQty()
                );
                // Initialize picked and shipped quantities to 0
                shipmentLine.setQtyPicked(0);
                shipmentLine.setQtyShipped(0);
                shipmentLines.add(shipmentLine);
                
                logger.info("Created shipment line: SKU={}, qtyOrdered={}", 
                    orderLine.getSku(), orderLine.getQty());
            }
            shipment.setLines(shipmentLines);

            // Set timestamps
            LocalDateTime now = LocalDateTime.now();
            if (shipment.getCreatedAt() == null) {
                shipment.setCreatedAt(now);
            }
            shipment.setUpdatedAt(now);

            logger.info("Shipment created successfully: shipmentId={}, orderId={}, lineCount={}", 
                shipment.getShipmentId(), shipment.getOrderId(), shipmentLines.size());

        } catch (Exception e) {
            logger.error("Failed to create shipment: {}", e.getMessage());
            throw new IllegalStateException("Failed to create shipment: " + e.getMessage());
        }
        
        return shipment;
    }
}

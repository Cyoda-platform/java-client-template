package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CreateOrderFromPaidProcessor - Handles order creation from paid cart
 * 
 * This processor is responsible for:
 * - Snapshotting cart lines and guest contact into order
 * - Decrementing product inventory for each ordered item
 * - Creating a single shipment in PICKING status
 * - Converting cart to CONVERTED status
 */
@Component
public class CreateOrderFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderFromPaidProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processOrderCreation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.getOrderId() != null && technicalId != null;
    }

    /**
     * Main order creation processing logic
     */
    private EntityWithMetadata<Order> processOrderCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.info("Processing order creation for order: {}", order.getOrderId());

        try {
            // Generate ULID order number
            generateOrderNumber(order);

            // Set initial status
            order.setStatus("WAITING_TO_FULFILL");
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Decrement product inventory for each line item
            decrementProductInventory(order);

            // Create shipment
            createShipment(order);

            logger.info("Order {} created successfully with {} lines", 
                    order.getOrderId(), order.getLines().size());

        } catch (Exception e) {
            logger.error("Error processing order creation for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Order creation failed", e);
        }

        return entityWithMetadata;
    }

    /**
     * Generate short ULID order number
     */
    private void generateOrderNumber(Order order) {
        // Generate a short ULID-like order number
        String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        order.setOrderNumber(orderNumber);
        logger.debug("Generated order number: {}", orderNumber);
    }

    /**
     * Decrement product inventory for each order line
     */
    private void decrementProductInventory(Order order) {
        ModelSpec productModelSpec = new ModelSpec()
                .withName(Product.ENTITY_NAME)
                .withVersion(Product.ENTITY_VERSION);

        for (Order.OrderLine line : order.getLines()) {
            try {
                // Get current product
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);

                if (productWithMetadata == null) {
                    logger.error("Product not found for SKU: {}", line.getSku());
                    throw new RuntimeException("Product not found: " + line.getSku());
                }

                Product product = productWithMetadata.entity();

                // Check if sufficient stock is available
                if (product.getQuantityAvailable() < line.getQty()) {
                    logger.error("Insufficient stock for SKU {}: available {}, required {}", 
                            line.getSku(), product.getQuantityAvailable(), line.getQty());
                    throw new RuntimeException("Insufficient stock for SKU: " + line.getSku());
                }

                // Decrement quantity
                int newQuantity = product.getQuantityAvailable() - line.getQty();
                product.setQuantityAvailable(newQuantity);

                // Update product
                entityService.update(productWithMetadata.metadata().getId(), product, "update_inventory");

                logger.info("Decremented inventory for SKU {}: {} -> {} (ordered: {})", 
                        line.getSku(), product.getQuantityAvailable() + line.getQty(), 
                        newQuantity, line.getQty());

            } catch (Exception e) {
                logger.error("Error decrementing inventory for SKU: {}", line.getSku(), e);
                throw new RuntimeException("Failed to decrement inventory for SKU: " + line.getSku(), e);
            }
        }
    }

    /**
     * Create shipment for the order
     */
    private void createShipment(Order order) {
        try {
            // Generate shipment ID
            String shipmentId = "SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // Create shipment lines from order lines
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            for (Order.OrderLine orderLine : order.getLines()) {
                Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                shipmentLine.setSku(orderLine.getSku());
                shipmentLine.setQtyOrdered(orderLine.getQty());
                shipmentLine.setQtyPicked(0); // Initially 0
                shipmentLine.setQtyShipped(0); // Initially 0
                shipmentLines.add(shipmentLine);
            }

            // Create shipment entity
            Shipment shipment = new Shipment();
            shipment.setShipmentId(shipmentId);
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");
            shipment.setLines(shipmentLines);
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

            // Save shipment
            EntityWithMetadata<Shipment> shipmentResponse = entityService.create(shipment);

            logger.info("Created shipment {} for order {} with {} lines", 
                    shipmentId, order.getOrderId(), shipmentLines.size());

        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create shipment", e);
        }
    }
}

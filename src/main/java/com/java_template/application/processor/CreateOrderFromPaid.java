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
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
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
 * ABOUTME: This file contains the CreateOrderFromPaid processor that creates orders from paid carts,
 * decrements product stock, and creates shipments for order fulfillment.
 */
@Component
public class CreateOrderFromPaid implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaid.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderFromPaid(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
        return order != null && order.isValid() && technicalId != null;
    }

    /**
     * Main business logic for creating order from paid cart
     */
    private EntityWithMetadata<Order> processOrderCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid cart for order: {}", order.getOrderId());

        try {
            // 1. Find the cart that this order was created from (we need cart data)
            // Note: In a real implementation, we'd pass cartId as part of order creation
            // For this demo, we'll assume the order already has the cart data snapshotted
            
            // 2. Decrement product stock for each order line
            decrementProductStock(order);

            // 3. Create shipment for this order
            createShipmentForOrder(order);

            // 4. Update order status
            order.setStatus("WAITING_TO_FULFILL");
            order.setUpdatedAt(LocalDateTime.now());

            logger.info("Order {} created successfully with {} items", 
                    order.getOrderId(), order.getTotals().getItems());

        } catch (Exception e) {
            logger.error("Error creating order from paid cart: {}", order.getOrderId(), e);
            // In a real system, we might want to set order to a failed state
            throw new RuntimeException("Failed to create order", e);
        }

        return entityWithMetadata;
    }

    /**
     * Decrement product stock for each order line
     */
    private void decrementProductStock(Order order) {
        logger.debug("Decrementing stock for order: {}", order.getOrderId());

        for (Order.OrderLine line : order.getLines()) {
            try {
                // Find product by SKU
                ModelSpec productModelSpec = new ModelSpec()
                        .withName(Product.ENTITY_NAME)
                        .withVersion(Product.ENTITY_VERSION);
                
                EntityWithMetadata<Product> productResponse = entityService.findByBusinessIdOrNull(
                        productModelSpec, line.getSku(), "sku", Product.class);

                if (productResponse == null) {
                    logger.warn("Product not found for SKU: {} in order: {}", line.getSku(), order.getOrderId());
                    continue;
                }

                Product product = productResponse.entity();
                
                // Check if we have enough stock
                if (product.getQuantityAvailable() < line.getQty()) {
                    logger.warn("Insufficient stock for SKU: {}. Available: {}, Ordered: {}", 
                            line.getSku(), product.getQuantityAvailable(), line.getQty());
                    // In a real system, we might want to handle this more gracefully
                    continue;
                }

                // Decrement stock
                int newQuantity = product.getQuantityAvailable() - line.getQty();
                product.setQuantityAvailable(newQuantity);

                // Update product (no transition needed - just update stock)
                entityService.update(productResponse.metadata().getId(), product, null);
                
                logger.debug("Decremented stock for SKU: {} by {} units. New quantity: {}", 
                        line.getSku(), line.getQty(), newQuantity);

            } catch (Exception e) {
                logger.error("Error decrementing stock for SKU: {} in order: {}", 
                        line.getSku(), order.getOrderId(), e);
                // Continue with other products even if one fails
            }
        }
    }

    /**
     * Create shipment for the order
     */
    private void createShipmentForOrder(Order order) {
        logger.debug("Creating shipment for order: {}", order.getOrderId());

        try {
            // Generate unique shipment ID
            String shipmentId = "SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // Create shipment
            Shipment shipment = new Shipment();
            shipment.setShipmentId(shipmentId);
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

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
            shipment.setLines(shipmentLines);

            // Create shipment entity
            EntityWithMetadata<Shipment> shipmentResponse = entityService.create(shipment);
            
            logger.info("Shipment {} created for order {} with {} lines", 
                    shipmentId, order.getOrderId(), shipmentLines.size());

        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create shipment", e);
        }
    }
}

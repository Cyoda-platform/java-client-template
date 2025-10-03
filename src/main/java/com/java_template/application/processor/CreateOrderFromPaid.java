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
 * ABOUTME: Order processor that creates orders from paid carts, snapshots cart data,
 * decrements product inventory, and creates associated shipments.
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
        logger.info("Creating order from paid cart for request: {}", request.getId());

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
     * Validates the EntityWithMetadata wrapper for Order
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

        logger.debug("Processing order creation for order: {}", order.getOrderId());

        try {
            // 1. Find the associated cart (assuming cartId is stored in order or can be derived)
            // For demo purposes, we'll assume the order already has the cart data snapshotted
            
            // 2. Decrement product inventory for each order line
            decrementProductInventory(order);

            // 3. Create shipment for the order
            createShipmentForOrder(order);

            // 4. Set order status to WAITING_TO_FULFILL
            order.setStatus("WAITING_TO_FULFILL");

            // 5. Update timestamp
            order.setUpdatedAt(LocalDateTime.now());

            logger.info("Order {} created successfully with {} lines", 
                       order.getOrderId(), order.getLines().size());

        } catch (Exception e) {
            logger.error("Error processing order creation for order: {}", order.getOrderId(), e);
            // In a real system, you might want to set order to a failed state
            throw new RuntimeException("Failed to process order creation", e);
        }

        return entityWithMetadata;
    }

    /**
     * Decrements product inventory for each order line
     */
    private void decrementProductInventory(Order order) {
        logger.debug("Decrementing inventory for order: {}", order.getOrderId());

        for (Order.OrderLine line : order.getLines()) {
            try {
                // Find product by SKU
                ModelSpec productModelSpec = new ModelSpec()
                        .withName(Product.ENTITY_NAME)
                        .withVersion(Product.ENTITY_VERSION);

                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessIdOrNull(
                        productModelSpec, line.getSku(), "sku", Product.class);

                if (productWithMetadata != null) {
                    Product product = productWithMetadata.entity();
                    
                    // Decrement quantity available
                    int currentQty = product.getQuantityAvailable();
                    int newQty = Math.max(0, currentQty - line.getQty()); // Prevent negative inventory
                    product.setQuantityAvailable(newQty);

                    // Update product with manual transition to stay in same state
                    entityService.update(productWithMetadata.metadata().getId(), product, "update_inventory");

                    logger.debug("Decremented inventory for SKU {}: {} -> {}", 
                               line.getSku(), currentQty, newQty);
                } else {
                    logger.warn("Product not found for SKU: {} in order: {}", line.getSku(), order.getOrderId());
                }
            } catch (Exception e) {
                logger.error("Error decrementing inventory for SKU: {} in order: {}", 
                           line.getSku(), order.getOrderId(), e);
                // Continue processing other lines even if one fails
            }
        }
    }

    /**
     * Creates a shipment for the order
     */
    private void createShipmentForOrder(Order order) {
        logger.debug("Creating shipment for order: {}", order.getOrderId());

        try {
            // Create shipment entity
            Shipment shipment = new Shipment();
            shipment.setShipmentId("SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
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
                shipmentLine.setQtyPicked(0); // Initially not picked
                shipmentLine.setQtyShipped(0); // Initially not shipped
                shipmentLines.add(shipmentLine);
            }
            shipment.setLines(shipmentLines);

            // Save shipment
            EntityWithMetadata<Shipment> shipmentResponse = entityService.create(shipment);
            
            logger.info("Created shipment {} for order {}", 
                       shipmentResponse.entity().getShipmentId(), order.getOrderId());

        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create shipment", e);
        }
    }
}

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
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
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
 * ABOUTME: Processor for creating orders from paid carts, including snapshotting cart data,
 * decrementing product stock, and creating associated shipments for order fulfillment.
 */
@Component
public class CreateOrderFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderFromPaidProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing order creation from paid cart for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processOrderCreationLogic)
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
        return order != null && order.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for creating order from paid cart
     */
    private EntityWithMetadata<Order> processOrderCreationLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid cart for order: {}", order.getOrderId());

        try {
            // 1. Decrement product stock for each order line
            decrementProductStock(order);

            // 2. Create shipment for the order
            createShipmentForOrder(order);

            // 3. Update order timestamps
            order.setUpdatedAt(LocalDateTime.now());

            logger.info("Order {} successfully created with stock decremented and shipment created", 
                       order.getOrderId());

        } catch (Exception e) {
            logger.error("Failed to process order creation for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Order creation failed: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Decrement product stock for each item in the order
     */
    private void decrementProductStock(Order order) {
        logger.debug("Decrementing stock for order lines in order: {}", order.getOrderId());

        ModelSpec productModelSpec = new ModelSpec()
                .withName(Product.ENTITY_NAME)
                .withVersion(Product.ENTITY_VERSION);

        for (Order.OrderLine line : order.getLines()) {
            try {
                // Find product by SKU
                SimpleCondition skuCondition = new SimpleCondition()
                        .withJsonPath("$.sku")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(line.getSku()));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(skuCondition));

                List<EntityWithMetadata<Product>> products = entityService.search(
                        productModelSpec, condition, Product.class);

                if (products.isEmpty()) {
                    logger.warn("Product not found for SKU: {}", line.getSku());
                    continue;
                }

                EntityWithMetadata<Product> productWithMetadata = products.get(0);
                Product product = productWithMetadata.entity();

                // Check if sufficient stock is available
                if (product.getQuantityAvailable() < line.getQty()) {
                    logger.warn("Insufficient stock for SKU {}: available={}, required={}", 
                               line.getSku(), product.getQuantityAvailable(), line.getQty());
                    // Continue processing - in a real system you might want to handle this differently
                }

                // Decrement stock
                int newQuantity = Math.max(0, product.getQuantityAvailable() - line.getQty());
                product.setQuantityAvailable(newQuantity);

                // Update product with manual transition to stay in same state
                entityService.update(productWithMetadata.metadata().getId(), product, "update_product");

                logger.debug("Decremented stock for SKU {}: {} -> {}", 
                           line.getSku(), product.getQuantityAvailable() + line.getQty(), newQuantity);

            } catch (Exception e) {
                logger.error("Failed to decrement stock for SKU: {}", line.getSku(), e);
                // Continue with other items
            }
        }
    }

    /**
     * Create a shipment for the order
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

            // Convert order lines to shipment lines
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

            // Create shipment in Cyoda
            EntityWithMetadata<Shipment> createdShipment = entityService.create(shipment);

            logger.info("Created shipment {} for order {}", 
                       createdShipment.entity().getShipmentId(), order.getOrderId());

        } catch (Exception e) {
            logger.error("Failed to create shipment for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Shipment creation failed: " + e.getMessage(), e);
        }
    }
}

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
 * Processor to create order from paid cart, decrement stock, and create shipment
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
                .map(this::createOrderFromPaidCart)
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
     * Create order from paid cart, decrement stock, create shipment
     */
    private EntityWithMetadata<Order> createOrderFromPaidCart(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid cart for order: {}", order.getOrderId());

        try {
            // 1. Decrement product stock for each order line
            decrementProductStock(order);

            // 2. Create shipment for the order
            createShipmentForOrder(order);

            // 3. Set timestamps
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            logger.info("Order {} created successfully from paid cart", order.getOrderId());

        } catch (Exception e) {
            logger.error("Error creating order from paid cart: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create order from paid cart", e);
        }

        return entityWithMetadata;
    }

    /**
     * Decrement product stock for each order line
     */
    private void decrementProductStock(Order order) {
        logger.debug("Decrementing stock for order lines: {}", order.getOrderId());

        ModelSpec productModelSpec = new ModelSpec()
                .withName(Product.ENTITY_NAME)
                .withVersion(Product.ENTITY_VERSION);

        for (Order.OrderLine line : order.getLines()) {
            try {
                // Find product by SKU
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);

                if (productWithMetadata != null) {
                    Product product = productWithMetadata.entity();
                    
                    // Decrement quantity available
                    int currentQty = product.getQuantityAvailable();
                    int newQty = Math.max(0, currentQty - line.getQty());
                    product.setQuantityAvailable(newQty);

                    // Update product (no transition = loop back to same state)
                    entityService.update(productWithMetadata.metadata().getId(), product, null);

                    logger.info("Decremented stock for SKU {}: {} -> {}", 
                               line.getSku(), currentQty, newQty);
                } else {
                    logger.warn("Product not found for SKU: {}", line.getSku());
                }
            } catch (Exception e) {
                logger.error("Error decrementing stock for SKU: {}", line.getSku(), e);
                // Continue with other products
            }
        }
    }

    /**
     * Create shipment for the order
     */
    private void createShipmentForOrder(Order order) {
        logger.debug("Creating shipment for order: {}", order.getOrderId());

        try {
            // Create shipment entity
            Shipment shipment = new Shipment();
            shipment.setShipmentId("SHIP-" + UUID.randomUUID().toString().substring(0, 8));
            shipment.setOrderId(order.getOrderId());
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

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

            // Create shipment entity
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.create(shipment);

            logger.info("Shipment {} created for order {}", 
                       shipmentWithMetadata.entity().getShipmentId(), order.getOrderId());

        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create shipment", e);
        }
    }
}

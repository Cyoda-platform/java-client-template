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
 * Processor to create an order from a paid cart.
 * This processor:
 * 1. Snapshots cart lines and guest contact into Order
 * 2. Decrements Product.quantityAvailable for each ordered item
 * 3. Creates one Shipment in PICKING state
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
     * Validates that the entity wrapper contains a valid order
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or Order entity is null");
            return false;
        }

        Order order = entityWithMetadata.entity();
        if (!order.isValid()) {
            logger.error("Order entity validation failed for orderId: {}", order.getOrderId());
            return false;
        }

        return true;
    }

    /**
     * Creates order from paid cart, decrements stock, and creates shipment
     */
    private Order createOrderFromPaidCart(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        logger.info("Creating order from paid cart for orderId: {}", order.getOrderId());

        try {
            // 1. Set order timestamps
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // 2. Decrement product quantities for each order line
            decrementProductQuantities(order.getLines());

            // 3. Create shipment for this order
            createShipmentForOrder(order);

            logger.info("Order {} successfully created with {} lines", order.getOrderId(), order.getLines().size());

        } catch (Exception e) {
            logger.error("Failed to create order from paid cart for orderId: {}", order.getOrderId(), e);
            throw new RuntimeException("Order creation failed", e);
        }

        return order;
    }

    /**
     * Decrements product quantities for ordered items
     */
    private void decrementProductQuantities(List<Order.OrderLine> orderLines) {
        logger.info("Decrementing product quantities for {} order lines", orderLines.size());

        for (Order.OrderLine line : orderLines) {
            try {
                // Find product by SKU
                ModelSpec productModelSpec = new ModelSpec();
                productModelSpec.setName(Product.ENTITY_NAME);
                productModelSpec.setVersion(Product.ENTITY_VERSION);

                List<EntityWithMetadata<Product>> products = entityService.findByBusinessId(
                    productModelSpec, "sku", line.getSku(), Product.class);

                if (products.isEmpty()) {
                    logger.warn("Product not found for SKU: {}", line.getSku());
                    continue;
                }

                EntityWithMetadata<Product> productWithMetadata = products.get(0);
                Product product = productWithMetadata.entity();

                // Decrement quantity
                int currentQty = product.getQuantityAvailable();
                int newQty = Math.max(0, currentQty - line.getQty());
                product.setQuantityAvailable(newQty);

                // Update product (manual transition to stay in same state)
                entityService.update(productWithMetadata.metadata().getId(), product, "update_product", Product.class);

                logger.info("Decremented product {} quantity from {} to {}", 
                           line.getSku(), currentQty, newQty);

            } catch (Exception e) {
                logger.error("Failed to decrement quantity for product SKU: {}", line.getSku(), e);
                // Continue processing other lines even if one fails
            }
        }
    }

    /**
     * Creates a shipment for the order
     */
    private void createShipmentForOrder(Order order) {
        logger.info("Creating shipment for order: {}", order.getOrderId());

        try {
            // Create shipment entity
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
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
                shipmentLine.setQtyPicked(0);
                shipmentLine.setQtyShipped(0);
                shipmentLines.add(shipmentLine);
            }
            shipment.setLines(shipmentLines);

            // Save shipment
            ModelSpec shipmentModelSpec = new ModelSpec();
            shipmentModelSpec.setName(Shipment.ENTITY_NAME);
            shipmentModelSpec.setVersion(Shipment.ENTITY_VERSION);

            entityService.save(shipmentModelSpec, shipment, "create_shipment", Shipment.class);

            logger.info("Shipment {} created for order {} with {} lines", 
                       shipment.getShipmentId(), order.getOrderId(), shipmentLines.size());

        } catch (Exception e) {
            logger.error("Failed to create shipment for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Shipment creation failed", e);
        }
    }
}

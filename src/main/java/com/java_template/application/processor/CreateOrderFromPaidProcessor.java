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
import de.huxhorn.sulky.ulid.ULID;
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
 * ABOUTME: Processor for creating orders from paid carts, including cart conversion,
 * stock decrementation, and shipment creation for order fulfillment.
 */
@Component
public class CreateOrderFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final ULID ulid;

    public CreateOrderFromPaidProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.ulid = new ULID();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::createOrderFromPaid)
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
        return order != null && order.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Create order from paid cart with stock decrementation and shipment creation
     */
    private EntityWithMetadata<Order> createOrderFromPaid(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid cart for order: {}", order.getOrderNumber());

        try {
            // Find the associated cart (assuming orderId matches cartId for this demo)
            Cart cart = findCartById(order.getOrderId());
            if (cart == null) {
                throw new RuntimeException("Cart not found for order: " + order.getOrderId());
            }

            // Decrement product stock for each line item
            decrementProductStock(order.getLines());

            // Create shipment for the order
            createShipmentForOrder(order);

            // Mark cart as converted
            markCartAsConverted(cart);

            // Update order timestamp
            order.setUpdatedAt(LocalDateTime.now());

            logger.info("Order {} created successfully from cart {}", order.getOrderNumber(), order.getOrderId());

        } catch (Exception e) {
            logger.error("Failed to create order from paid cart: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create order from paid cart", e);
        }

        return entityWithMetadata;
    }

    /**
     * Find cart by cart ID
     */
    private Cart findCartById(String cartId) {
        try {
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);
            return cartWithMetadata != null ? cartWithMetadata.entity() : null;
        } catch (Exception e) {
            logger.error("Failed to find cart: {}", cartId, e);
            return null;
        }
    }

    /**
     * Decrement product stock for order line items
     */
    private void decrementProductStock(List<Order.OrderLine> orderLines) {
        for (Order.OrderLine line : orderLines) {
            try {
                // Find product by SKU
                ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);

                if (productWithMetadata != null) {
                    Product product = productWithMetadata.entity();
                    
                    // Decrement quantity available
                    int currentQty = product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0;
                    int newQty = Math.max(0, currentQty - line.getQty());
                    product.setQuantityAvailable(newQty);

                    // Update product with inventory transition
                    entityService.update(productWithMetadata.metadata().getId(), product, "update_inventory");
                    
                    logger.debug("Decremented stock for SKU: {} from {} to {}", line.getSku(), currentQty, newQty);
                } else {
                    logger.warn("Product not found for SKU: {}", line.getSku());
                }
            } catch (Exception e) {
                logger.error("Failed to decrement stock for SKU: {}", line.getSku(), e);
            }
        }
    }

    /**
     * Create shipment for the order
     */
    private void createShipmentForOrder(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

            // Convert order lines to shipment lines
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

            entityService.create(shipment);
            logger.debug("Created shipment: {} for order: {}", shipment.getShipmentId(), order.getOrderId());
        } catch (Exception e) {
            logger.error("Failed to create shipment for order: {}", order.getOrderId(), e);
        }
    }

    /**
     * Mark cart as converted
     */
    private void markCartAsConverted(Cart cart) {
        try {
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cart.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata != null) {
                Cart updatedCart = cartWithMetadata.entity();
                updatedCart.setStatus("CONVERTED");
                updatedCart.setUpdatedAt(LocalDateTime.now());

                entityService.update(cartWithMetadata.metadata().getId(), updatedCart, "checkout");
                logger.debug("Marked cart as converted: {}", cart.getCartId());
            }
        } catch (Exception e) {
            logger.error("Failed to mark cart as converted: {}", cart.getCartId(), e);
        }
    }
}

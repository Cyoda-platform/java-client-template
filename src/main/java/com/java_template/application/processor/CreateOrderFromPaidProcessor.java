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
 * ABOUTME: Processor for creating orders from paid carts, handling cart snapshot,
 * inventory decrements, and shipment creation for order fulfillment.
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
     * Validates the EntityWithMetadata wrapper for Order
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for order creation from paid cart
     */
    private EntityWithMetadata<Order> processOrderCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing order creation for orderId: {}", order.getOrderId());

        // Get the cart that this order is based on
        Cart cart = getCartForOrder(order);

        // Snapshot cart data into order
        snapshotCartToOrder(order, cart);

        // Decrement product inventory for each line
        decrementProductInventory(order);

        // Create shipment for this order
        createShipmentForOrder(order);

        // Set order status and timestamps
        order.setStatus("WAITING_TO_FULFILL");
        order.setUpdatedAt(LocalDateTime.now());

        logger.info("Order {} created successfully from cart {}", order.getOrderId(), order.getCartId());

        return entityWithMetadata;
    }

    /**
     * Get the cart that this order is based on
     */
    private Cart getCartForOrder(Order order) {
        if (order.getCartId() == null) {
            throw new IllegalStateException("Order must have cartId to create from cart: " + order.getOrderId());
        }

        ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
        EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                cartModelSpec, order.getCartId(), "cartId", Cart.class);

        if (cartWithMetadata == null) {
            throw new IllegalStateException("Cart not found for order: " + order.getOrderId() + ", cartId: " + order.getCartId());
        }

        Cart cart = cartWithMetadata.entity();
        if (!"CONVERTED".equals(cart.getStatus())) {
            throw new IllegalStateException("Cart must be CONVERTED to create order, current status: " + cart.getStatus());
        }

        return cart;
    }

    /**
     * Snapshot cart data into order
     */
    private void snapshotCartToOrder(Order order, Cart cart) {
        // Copy lines from cart to order
        List<Order.OrderLine> orderLines = new ArrayList<>();
        for (Cart.CartLine cartLine : cart.getLines()) {
            Order.OrderLine orderLine = new Order.OrderLine();
            orderLine.setSku(cartLine.getSku());
            orderLine.setName(cartLine.getName());
            orderLine.setUnitPrice(cartLine.getPrice());
            orderLine.setQty(cartLine.getQty());
            orderLine.setLineTotal(cartLine.getLineTotal());
            orderLines.add(orderLine);
        }
        order.setLines(orderLines);

        // Copy totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(cart.getTotalItems());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);

        // Copy guest contact
        if (cart.getGuestContact() != null) {
            Order.OrderGuestContact orderContact = new Order.OrderGuestContact();
            orderContact.setName(cart.getGuestContact().getName());
            orderContact.setEmail(cart.getGuestContact().getEmail());
            orderContact.setPhone(cart.getGuestContact().getPhone());

            if (cart.getGuestContact().getAddress() != null) {
                Order.OrderAddress orderAddress = new Order.OrderAddress();
                orderAddress.setLine1(cart.getGuestContact().getAddress().getLine1());
                orderAddress.setCity(cart.getGuestContact().getAddress().getCity());
                orderAddress.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                orderAddress.setCountry(cart.getGuestContact().getAddress().getCountry());
                orderContact.setAddress(orderAddress);
            }

            order.setGuestContact(orderContact);
        }

        logger.debug("Cart data snapshotted to order: {}", order.getOrderId());
    }

    /**
     * Decrement product inventory for each order line
     */
    private void decrementProductInventory(Order order) {
        ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);

        for (Order.OrderLine line : order.getLines()) {
            try {
                // Get product
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);

                if (productWithMetadata != null) {
                    Product product = productWithMetadata.entity();
                    
                    // Decrement quantity available
                    int newQuantity = product.getQuantityAvailable() - line.getQty();
                    product.setQuantityAvailable(Math.max(0, newQuantity)); // Don't go below 0

                    // Update product with inventory transition
                    entityService.update(productWithMetadata.metadata().getId(), product, "update_inventory");
                    
                    logger.debug("Decremented inventory for SKU: {}, qty: {}, new available: {}", 
                               line.getSku(), line.getQty(), product.getQuantityAvailable());
                } else {
                    logger.warn("Product not found for inventory decrement: {}", line.getSku());
                }
            } catch (Exception e) {
                logger.error("Failed to decrement inventory for SKU: {}", line.getSku(), e);
                // Continue processing other lines even if one fails
            }
        }
    }

    /**
     * Create shipment for this order
     */
    private void createShipmentForOrder(Order order) {
        try {
            String shipmentId = "SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            Shipment shipment = new Shipment();
            shipment.setShipmentId(shipmentId);
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

            // Copy lines from order to shipment
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            for (Order.OrderLine orderLine : order.getLines()) {
                Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                shipmentLine.setSku(orderLine.getSku());
                shipmentLine.setQtyOrdered(orderLine.getQty());
                shipmentLine.setQtyPicked(0); // Will be updated during picking
                shipmentLine.setQtyShipped(0); // Will be updated when shipped
                shipmentLines.add(shipmentLine);
            }
            shipment.setLines(shipmentLines);

            entityService.create(shipment);
            logger.info("Shipment {} created for order {}", shipmentId, order.getOrderId());
        } catch (Exception e) {
            logger.error("Failed to create shipment for order: {}", order.getOrderId(), e);
            // Don't fail the order creation if shipment creation fails
        }
    }
}

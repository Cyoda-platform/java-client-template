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
 * Processor to create order from paid payment
 * Snapshots cart data, decrements product stock, and creates shipment
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
     * Validates the EntityWithMetadata wrapper for Order
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid() && technicalId != null;
    }

    /**
     * Main business logic for creating order from paid payment
     */
    private EntityWithMetadata<Order> processOrderCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid payment for order: {}", order.getOrderId());

        try {
            // Find the cart to snapshot data from
            Cart cart = findCartForOrder(order);
            if (cart == null) {
                throw new RuntimeException("Cart not found for order: " + order.getOrderId());
            }

            // Snapshot cart data into order
            snapshotCartToOrder(order, cart);

            // Decrement product stock for each line item
            decrementProductStock(order.getLines());

            // Create shipment for the order
            createShipmentForOrder(order);

            // Set order status and timestamps
            order.setStatus("WAITING_TO_FULFILL");
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Generate short ULID for order number
            order.setOrderNumber(generateShortULID());

            logger.info("Order {} created successfully with order number {}", 
                       order.getOrderId(), order.getOrderNumber());

        } catch (Exception e) {
            logger.error("Error creating order from paid payment: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create order: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Find the cart associated with this order
     * Note: This assumes the order has a reference to the cart somehow
     */
    private Cart findCartForOrder(Order order) {
        // For this demo, we'll assume the orderId contains or references the cartId
        // In a real implementation, you might have a different relationship
        String cartId = order.getOrderId(); // Simplified assumption
        
        ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
        EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                cartModelSpec, cartId, "cartId", Cart.class);
        
        return cartWithMetadata != null ? cartWithMetadata.entity() : null;
    }

    /**
     * Snapshot cart data into order
     */
    private void snapshotCartToOrder(Order order, Cart cart) {
        // Convert cart lines to order lines
        List<Order.OrderLine> orderLines = new ArrayList<>();
        if (cart.getLines() != null) {
            for (Cart.CartLine cartLine : cart.getLines()) {
                Order.OrderLine orderLine = new Order.OrderLine();
                orderLine.setSku(cartLine.getSku());
                orderLine.setName(cartLine.getName());
                orderLine.setUnitPrice(cartLine.getPrice());
                orderLine.setQty(cartLine.getQty());
                orderLine.setLineTotal(cartLine.getLineTotal());
                orderLines.add(orderLine);
            }
        }
        order.setLines(orderLines);

        // Set totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(cart.getGrandTotal());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);

        // Copy guest contact information
        if (cart.getGuestContact() != null) {
            Order.OrderGuestContact orderContact = new Order.OrderGuestContact();
            orderContact.setName(cart.getGuestContact().getName());
            orderContact.setEmail(cart.getGuestContact().getEmail());
            orderContact.setPhone(cart.getGuestContact().getPhone());
            
            if (cart.getGuestContact().getAddress() != null) {
                Order.OrderAddress orderAddress = new Order.OrderAddress();
                orderAddress.setLine1(cart.getGuestContact().getAddress().getLine1());
                orderAddress.setLine2(cart.getGuestContact().getAddress().getLine2());
                orderAddress.setCity(cart.getGuestContact().getAddress().getCity());
                orderAddress.setState(cart.getGuestContact().getAddress().getState());
                orderAddress.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                orderAddress.setCountry(cart.getGuestContact().getAddress().getCountry());
                orderContact.setAddress(orderAddress);
            }
            
            order.setGuestContact(orderContact);
        }
    }

    /**
     * Decrement product stock for each order line
     */
    private void decrementProductStock(List<Order.OrderLine> orderLines) {
        if (orderLines == null) return;

        ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);

        for (Order.OrderLine line : orderLines) {
            try {
                // Find product by SKU
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);

                if (productWithMetadata != null) {
                    Product product = productWithMetadata.entity();
                    
                    // Decrement quantity available
                    int currentQty = product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0;
                    int orderedQty = line.getQty() != null ? line.getQty() : 0;
                    int newQty = Math.max(0, currentQty - orderedQty);
                    
                    product.setQuantityAvailable(newQty);
                    
                    // Update product with manual transition
                    entityService.update(productWithMetadata.metadata().getId(), product, "update_product");
                    
                    logger.debug("Decremented stock for SKU {}: {} -> {}", 
                               line.getSku(), currentQty, newQty);
                } else {
                    logger.warn("Product not found for SKU: {}", line.getSku());
                }
            } catch (Exception e) {
                logger.error("Error decrementing stock for SKU: {}", line.getSku(), e);
                // Continue processing other items
            }
        }
    }

    /**
     * Create shipment for the order
     */
    private void createShipmentForOrder(Order order) {
        Shipment shipment = new Shipment();
        shipment.setShipmentId(UUID.randomUUID().toString());
        shipment.setOrderId(order.getOrderId());
        shipment.setStatus("PICKING");
        shipment.setCreatedAt(LocalDateTime.now());
        shipment.setUpdatedAt(LocalDateTime.now());

        // Convert order lines to shipment lines
        List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
        if (order.getLines() != null) {
            for (Order.OrderLine orderLine : order.getLines()) {
                Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                shipmentLine.setSku(orderLine.getSku());
                shipmentLine.setQtyOrdered(orderLine.getQty());
                shipmentLine.setQtyPicked(0);
                shipmentLine.setQtyShipped(0);
                shipmentLines.add(shipmentLine);
            }
        }
        shipment.setLines(shipmentLines);

        // Create shipment entity
        entityService.create(shipment);
        
        logger.debug("Created shipment {} for order {}", shipment.getShipmentId(), order.getOrderId());
    }

    /**
     * Generate a short ULID-like identifier for order number
     * This is a simplified implementation for demo purposes
     */
    private String generateShortULID() {
        // Generate a short unique identifier (simplified ULID)
        long timestamp = System.currentTimeMillis();
        String randomPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("ORD-%d-%s", timestamp % 1000000, randomPart);
    }
}

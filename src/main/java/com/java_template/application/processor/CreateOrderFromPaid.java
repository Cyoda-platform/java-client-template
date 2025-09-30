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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Processor to create order from paid payment
 * - Snapshot cart lines + guestContact into Order
 * - Decrement each Product.quantityAvailable by ordered qty
 * - Create one Shipment in PICKING state
 * Used in Order workflow transition: create_order_from_paid
 */
@Component
public class CreateOrderFromPaid implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaid.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateOrderFromPaid(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
     * Main business logic to create order from paid payment
     */
    private EntityWithMetadata<Order> processOrderCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid payment for order: {}", order.getOrderId());

        try {
            // 1. Find the cart to snapshot data from
            Cart cart = findCartForOrder(order);
            if (cart == null) {
                throw new RuntimeException("Cart not found for order creation");
            }

            // 2. Snapshot cart data into order
            snapshotCartToOrder(cart, order);

            // 3. Decrement product quantities
            decrementProductQuantities(order.getLines());

            // 4. Create shipment
            createShipmentForOrder(order);

            // 5. Update timestamps
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            logger.info("Order {} created successfully from cart with {} items", 
                       order.getOrderId(), order.getLines().size());

        } catch (Exception e) {
            logger.error("Failed to create order {}: {}", order.getOrderId(), e.getMessage(), e);
            throw new RuntimeException("Order creation failed: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Find the cart associated with this order (assuming cartId is stored in order or can be derived)
     * For this demo, we'll assume the orderId contains reference to cartId or we can find it another way
     */
    private Cart findCartForOrder(Order order) {
        // In a real implementation, you'd have a way to link order to cart
        // For demo purposes, let's assume we can derive cartId from orderId or it's stored in order
        // This is a simplified approach - in practice you'd have proper linking
        
        ModelSpec cartModelSpec = new ModelSpec()
                .withName(Cart.ENTITY_NAME)
                .withVersion(Cart.ENTITY_VERSION);
        
        // For demo, let's try to find a cart that's in CONVERTED state
        // In practice, you'd have proper order-to-cart linking
        List<EntityWithMetadata<Cart>> carts = entityService.findAll(cartModelSpec, Cart.class);
        
        // Find the first cart in converted state (this is simplified for demo)
        return carts.stream()
                .filter(cartWithMetadata -> "converted".equals(cartWithMetadata.metadata().getState()))
                .map(EntityWithMetadata::entity)
                .findFirst()
                .orElse(null);
    }

    /**
     * Snapshot cart data into order
     */
    private void snapshotCartToOrder(Cart cart, Order order) {
        // Convert cart lines to order lines
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

        // Set totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(cart.getGrandTotal());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);

        // Copy guest contact if available
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
    }

    /**
     * Decrement product quantities for ordered items
     */
    private void decrementProductQuantities(List<Order.OrderLine> orderLines) {
        ModelSpec productModelSpec = new ModelSpec()
                .withName(Product.ENTITY_NAME)
                .withVersion(Product.ENTITY_VERSION);

        for (Order.OrderLine line : orderLines) {
            try {
                // Find product by SKU
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);
                
                if (productWithMetadata != null) {
                    Product product = productWithMetadata.entity();
                    
                    // Decrement quantity
                    int currentQty = product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0;
                    int orderedQty = line.getQty() != null ? line.getQty() : 0;
                    int newQty = Math.max(0, currentQty - orderedQty);
                    
                    product.setQuantityAvailable(newQty);
                    
                    // Update product (use manual transition to stay in same state)
                    entityService.update(productWithMetadata.metadata().getId(), product, null);
                    
                    logger.debug("Decremented product {} quantity from {} to {} (ordered: {})", 
                               line.getSku(), currentQty, newQty, orderedQty);
                } else {
                    logger.warn("Product not found for SKU: {}", line.getSku());
                }
            } catch (Exception e) {
                logger.error("Failed to decrement quantity for product {}: {}", line.getSku(), e.getMessage());
                // Continue processing other products
            }
        }
    }

    /**
     * Create shipment for the order
     */
    private void createShipmentForOrder(Order order) {
        Shipment shipment = new Shipment();
        shipment.setShipmentId("SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        shipment.setOrderId(order.getOrderId());
        
        // Convert order lines to shipment lines
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
        
        shipment.setCreatedAt(LocalDateTime.now());
        shipment.setUpdatedAt(LocalDateTime.now());

        // Create shipment entity
        EntityWithMetadata<Shipment> createdShipment = entityService.create(shipment);
        
        logger.info("Created shipment {} for order {}", 
                   createdShipment.entity().getShipmentId(), order.getOrderId());
    }
}

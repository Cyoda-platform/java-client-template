package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
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
 * OrderCreateFromPaidProcessor - Creates order from paid cart, decrements product stock, and creates associated shipment.
 * 
 * Transitions: CREATE_ORDER
 * 
 * Business Logic:
 * - Validates payment is PAID
 * - Validates cart is CHECKING_OUT
 * - Generates short ULID for order number
 * - Snapshots cart data to order
 * - Decrements product stock
 * - Creates shipment
 * - Updates cart to CONVERTED
 */
@Component
public class OrderCreateFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreateFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderCreateFromPaidProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order creation from paid cart for request: {}", request.getId());

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
        return order != null && technicalId != null;
    }

    /**
     * Main business logic for order creation
     */
    private EntityWithMetadata<Order> processOrderCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid cart and payment");

        // Extract cartId and paymentId from order context (these should be provided in the order entity)
        String cartId = order.getOrderId(); // Assuming cartId is passed in orderId initially
        String paymentId = order.getOrderNumber(); // Assuming paymentId is passed in orderNumber initially

        // Validate payment is PAID
        Payment payment = validatePayment(paymentId);
        
        // Get cart data and validate state
        Cart cart = validateCart(cartId);

        // Generate short ULID for order number
        String orderNumber = generateShortULID();
        order.setOrderNumber(orderNumber);

        // Snapshot cart data to order
        snapshotCartToOrder(cart, order);

        // Decrement product stock
        decrementProductStock(order.getLines());

        // Create shipment
        createShipment(order);

        // Update cart to CONVERTED
        updateCartToConverted(cart);

        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        logger.info("Order {} created successfully with order number {}", order.getOrderId(), orderNumber);

        return entityWithMetadata;
    }

    /**
     * Validates payment exists and is in PAID state
     */
    private Payment validatePayment(String paymentId) {
        try {
            ModelSpec paymentModelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);
            
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, paymentId, "paymentId", Payment.class);
            
            Payment payment = paymentWithMetadata.entity();
            String paymentState = paymentWithMetadata.metadata().getState();
            
            if (!"paid".equals(paymentState)) {
                throw new IllegalStateException("Payment must be in PAID state, current state: " + paymentState);
            }
            
            return payment;
        } catch (Exception e) {
            logger.error("Payment validation failed for paymentId: {}", paymentId);
            throw new IllegalArgumentException("Payment not found or not paid: " + paymentId, e);
        }
    }

    /**
     * Validates cart exists and is in CHECKING_OUT state
     */
    private Cart validateCart(String cartId) {
        try {
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);
            
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);
            
            Cart cart = cartWithMetadata.entity();
            String cartState = cartWithMetadata.metadata().getState();
            
            if (!"CHECKING_OUT".equals(cartState)) {
                throw new IllegalStateException("Cart must be in CHECKING_OUT state, current state: " + cartState);
            }
            
            return cart;
        } catch (Exception e) {
            logger.error("Cart validation failed for cartId: {}", cartId);
            throw new IllegalArgumentException("Cart not found or not in checkout: " + cartId, e);
        }
    }

    /**
     * Generates a short ULID-like identifier for order number
     */
    private String generateShortULID() {
        // Generate a short ULID-like identifier (simplified version)
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return uuid.substring(0, 10); // Take first 10 characters for short ULID
    }

    /**
     * Snapshots cart data to order
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
            orderLine.setLineTotal(cartLine.getPrice() * cartLine.getQty());
            orderLines.add(orderLine);
        }
        order.setLines(orderLines);

        // Set totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(cart.getTotalItems());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);

        // Set guest contact
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
     * Decrements product stock for each order line
     */
    private void decrementProductStock(List<Order.OrderLine> orderLines) {
        for (Order.OrderLine line : orderLines) {
            try {
                ModelSpec productModelSpec = new ModelSpec()
                        .withName(Product.ENTITY_NAME)
                        .withVersion(Product.ENTITY_VERSION);
                
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);
                
                Product product = productWithMetadata.entity();
                
                // Decrement quantity
                int newQuantity = product.getQuantityAvailable() - line.getQty();
                if (newQuantity < 0) {
                    throw new IllegalStateException("Insufficient stock for SKU: " + line.getSku());
                }
                
                product.setQuantityAvailable(newQuantity);
                product.setUpdatedAt(LocalDateTime.now());
                
                // Update product without transition (no state change)
                entityService.update(productWithMetadata.metadata().getId(), product, null);
                
                logger.debug("Decremented stock for SKU: {} by {} units, new quantity: {}", 
                           line.getSku(), line.getQty(), newQuantity);
                
            } catch (Exception e) {
                logger.error("Failed to decrement stock for SKU: {}", line.getSku());
                throw new RuntimeException("Stock decrement failed for SKU: " + line.getSku(), e);
            }
        }
    }

    /**
     * Creates shipment for the order
     */
    private void createShipment(Order order) {
        Shipment shipment = new Shipment();
        shipment.setShipmentId(UUID.randomUUID().toString());
        shipment.setOrderId(order.getOrderId());
        
        // Create shipment lines
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
        
        LocalDateTime now = LocalDateTime.now();
        shipment.setCreatedAt(now);
        shipment.setUpdatedAt(now);
        
        // Create shipment with CREATE_SHIPMENT transition (assuming this exists in workflow)
        entityService.create(shipment);
        
        logger.debug("Created shipment {} for order {}", shipment.getShipmentId(), order.getOrderId());
    }

    /**
     * Updates cart to CONVERTED state
     */
    private void updateCartToConverted(Cart cart) {
        try {
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);
            
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cart.getCartId(), "cartId", Cart.class);
            
            cart.setUpdatedAt(LocalDateTime.now());
            
            // Update cart with COMPLETE_CHECKOUT transition
            entityService.update(cartWithMetadata.metadata().getId(), cart, "COMPLETE_CHECKOUT");
            
            logger.debug("Updated cart {} to CONVERTED state", cart.getCartId());
            
        } catch (Exception e) {
            logger.error("Failed to update cart to CONVERTED state: {}", cart.getCartId());
            throw new RuntimeException("Cart update failed: " + cart.getCartId(), e);
        }
    }
}

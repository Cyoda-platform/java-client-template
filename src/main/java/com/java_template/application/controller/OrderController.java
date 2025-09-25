package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order Controller for OMS system
 * Handles order creation from paid carts and order status management
 */
@RestController
@RequestMapping("/ui/order")
@CrossOrigin(origins = "*")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final EntityService entityService;

    public OrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create order from paid cart
     * POST /ui/order/create
     */
    @PostMapping("/create")
    public ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request) {
        try {
            // Validate payment is PAID
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                logger.error("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            
            if (!"PAID".equals(payment.getStatus())) {
                logger.error("Payment {} is not PAID: {}", request.getPaymentId(), payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.error("Cart not found: {}", request.getCartId());
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Create order from cart
            Order order = createOrderFromCart(cart);
            
            EntityWithMetadata<Order> orderResponse = entityService.create(order);
            
            // Mark cart as CONVERTED
            entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");
            
            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(order.getStatus());
            
            logger.info("Order {} created from cart {} and payment {}", 
                       order.getOrderId(), request.getCartId(), request.getPaymentId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by order ID
     * GET /ui/order/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrder(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> order = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (order == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by technical ID
     * GET /ui/order/id/{id}
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> order = entityService.getById(id, modelSpec, Order.class);
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            logger.error("Error getting order by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update order status (manual transitions)
     * POST /ui/order/{orderId}/transition/{transitionName}
     */
    @PostMapping("/{orderId}/transition/{transitionName}")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderStatus(
            @PathVariable String orderId,
            @PathVariable String transitionName) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderWithMetadata.entity();
            
            EntityWithMetadata<Order> response = entityService.update(
                    orderWithMetadata.metadata().getId(), order, transitionName);
            
            logger.info("Order {} transitioned via {}", orderId, transitionName);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating order status: {} -> {}", orderId, transitionName, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create Order entity from Cart
     */
    private Order createOrderFromCart(Cart cart) {
        Order order = new Order();
        
        // Generate order identifiers
        order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setOrderNumber(generateShortULID());
        order.setStatus("WAITING_TO_FULFILL");
        
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
        totals.setItems(cart.getTotalItems());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);
        
        // Copy guest contact
        if (cart.getGuestContact() != null) {
            Order.OrderGuestContact guestContact = new Order.OrderGuestContact();
            guestContact.setName(cart.getGuestContact().getName());
            guestContact.setEmail(cart.getGuestContact().getEmail());
            guestContact.setPhone(cart.getGuestContact().getPhone());
            
            if (cart.getGuestContact().getAddress() != null) {
                Order.OrderAddress address = new Order.OrderAddress();
                address.setLine1(cart.getGuestContact().getAddress().getLine1());
                address.setCity(cart.getGuestContact().getAddress().getCity());
                address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                address.setCountry(cart.getGuestContact().getAddress().getCountry());
                guestContact.setAddress(address);
            }
            
            order.setGuestContact(guestContact);
        }
        
        // Set timestamps
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        
        return order;
    }

    /**
     * Generate a short ULID-like identifier for order number
     */
    private String generateShortULID() {
        // Simple implementation - in production, use proper ULID library
        return "UL" + System.currentTimeMillis() % 1000000;
    }

    // Request/Response DTOs

    @Getter
    @Setter
    public static class OrderCreateRequest {
        private String paymentId;
        private String cartId;
    }

    @Getter
    @Setter
    public static class OrderCreateResponse {
        private String orderId;
        private String orderNumber;
        private String status;
    }
}

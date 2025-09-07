package com.java_template.application.controller;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order Controller - Manages order creation and tracking
 * 
 * Endpoints:
 * - POST /ui/order/create - Create order from paid payment
 * - GET /ui/order/{orderId} - Get order details for confirmation/status
 * - PATCH /ui/order/{orderId}/status - Update order status (for warehouse operations)
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
     * Create order from paid payment
     * POST /ui/order/create
     */
    @PostMapping("/create")
    public ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request) {
        try {
            // Get payment by business ID
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentResponse = entityService.findByBusinessId(
                paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);
            
            if (paymentResponse == null) {
                logger.error("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.badRequest().build();
            }
            
            // Validate payment is PAID
            String paymentState = paymentResponse.getState();
            if (!"paid".equals(paymentState)) {
                logger.error("Payment {} is not paid, current state: {}", request.getPaymentId(), paymentState);
                return ResponseEntity.badRequest().build();
            }

            Payment payment = paymentResponse.entity();
            
            // Get cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                cartModelSpec, payment.getCartId(), "cartId", Cart.class);
            
            if (cartResponse == null) {
                logger.error("Cart not found: {}", payment.getCartId());
                return ResponseEntity.badRequest().build();
            }
            
            Cart cart = cartResponse.entity();

            // Validate cart has guest contact
            if (cart.getGuestContact() == null || cart.getGuestContact().getName() == null) {
                logger.error("Cart {} does not have guest contact", payment.getCartId());
                return ResponseEntity.badRequest().build();
            }
            
            // Create order
            Order order = new Order();
            order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            order.setOrderNumber(generateShortULID());
            order.setLines(mapCartLinesToOrderLines(cart.getLines()));
            
            Order.OrderTotals totals = new Order.OrderTotals();
            totals.setItems(cart.getTotalItems());
            totals.setGrand(cart.getGrandTotal());
            order.setTotals(totals);
            
            order.setGuestContact(mapCartGuestContactToOrderGuestContact(cart.getGuestContact()));
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            
            EntityWithMetadata<Order> orderResponse = entityService.create(order);
            
            // Update cart to CONVERTED state
            entityService.update(cartResponse.getId(), cart, "CHECKOUT");
            
            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus("WAITING_TO_FULFILL");
            response.setMessage("Order created successfully");
            
            logger.info("Order created: {} from payment: {}", order.getOrderId(), request.getPaymentId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order details for confirmation/status
     * GET /ui/order/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrder(@PathVariable String orderId) {
        try {
            ModelSpec orderModelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.findByBusinessId(
                orderModelSpec, orderId, "orderId", Order.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order status (for warehouse operations)
     * PATCH /ui/order/{orderId}/status
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody OrderStatusUpdateRequest request) {
        try {
            ModelSpec orderModelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderResponse = entityService.findByBusinessId(
                orderModelSpec, orderId, "orderId", Order.class);
            
            if (orderResponse == null) {
                return ResponseEntity.notFound().build();
            }
            
            Order order = orderResponse.entity();
            order.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Order> response = entityService.update(
                orderResponse.getId(), order, request.getTransition());
            
            logger.info("Order status updated: {} with transition: {}", orderId, request.getTransition());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order status", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods

    private String generateShortULID() {
        // Simple ULID-like generation for demo purposes
        return "01" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private List<Order.OrderLine> mapCartLinesToOrderLines(List<Cart.CartLine> cartLines) {
        List<Order.OrderLine> orderLines = new ArrayList<>();
        for (Cart.CartLine cartLine : cartLines) {
            Order.OrderLine orderLine = new Order.OrderLine();
            orderLine.setSku(cartLine.getSku());
            orderLine.setName(cartLine.getName());
            orderLine.setUnitPrice(cartLine.getPrice());
            orderLine.setQty(cartLine.getQty());
            orderLine.setLineTotal(cartLine.getPrice() * cartLine.getQty());
            orderLines.add(orderLine);
        }
        return orderLines;
    }

    private Order.OrderGuestContact mapCartGuestContactToOrderGuestContact(Cart.CartGuestContact cartContact) {
        Order.OrderGuestContact orderContact = new Order.OrderGuestContact();
        orderContact.setName(cartContact.getName());
        orderContact.setEmail(cartContact.getEmail());
        orderContact.setPhone(cartContact.getPhone());
        
        if (cartContact.getAddress() != null) {
            Order.OrderAddress orderAddress = new Order.OrderAddress();
            orderAddress.setLine1(cartContact.getAddress().getLine1());
            orderAddress.setCity(cartContact.getAddress().getCity());
            orderAddress.setPostcode(cartContact.getAddress().getPostcode());
            orderAddress.setCountry(cartContact.getAddress().getCountry());
            orderContact.setAddress(orderAddress);
        }
        
        return orderContact;
    }

    // Request and Response DTOs

    public static class OrderCreateRequest {
        private String paymentId;
        private String cartId;

        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        
        public String getCartId() { return cartId; }
        public void setCartId(String cartId) { this.cartId = cartId; }
    }

    public static class OrderCreateResponse {
        private String orderId;
        private String orderNumber;
        private String status;
        private String message;

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        
        public String getOrderNumber() { return orderNumber; }
        public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class OrderStatusUpdateRequest {
        private String transition;

        public String getTransition() { return transition; }
        public void setTransition(String transition) { this.transition = transition; }
    }
}

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
import java.util.UUID;

/**
 * Order Controller for OMS
 * Provides endpoints for order creation and retrieval
 * Maps to /ui/order/** endpoints
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
     * Body: { paymentId, cartId }
     * Preconditions: Payment PAID
     * Returns: { orderId, orderNumber, status }
     */
    @PostMapping("/create")
    public ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request) {
        try {
            // Validate payment is PAID
            EntityWithMetadata<Payment> paymentWithMetadata = getPaymentByBusinessId(request.getPaymentId());
            if (paymentWithMetadata == null) {
                logger.warn("Payment not found for order creation: {}", request.getPaymentId());
                return ResponseEntity.badRequest().build();
            }

            String paymentState = paymentWithMetadata.metadata().getState();
            if (!"paid".equals(paymentState)) {
                logger.warn("Payment not in PAID state for order creation: {} (state: {})", 
                           request.getPaymentId(), paymentState);
                return ResponseEntity.badRequest().build();
            }

            // Validate cart exists
            EntityWithMetadata<Cart> cartWithMetadata = getCartByBusinessId(request.getCartId());
            if (cartWithMetadata == null) {
                logger.warn("Cart not found for order creation: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Create order entity
            Order order = new Order();
            order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            order.setOrderNumber(generateShortULID()); // Short ULID as specified
            
            // Initialize with basic data (will be populated by CreateOrderFromPaid processor)
            order.setLines(java.util.Collections.emptyList());
            order.setTotals(new Order.OrderTotals());
            
            // Set guest contact from cart if available
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
            } else {
                // Create minimal guest contact to satisfy validation
                Order.OrderGuestContact orderContact = new Order.OrderGuestContact();
                orderContact.setName("Guest Customer");
                Order.OrderAddress orderAddress = new Order.OrderAddress();
                orderAddress.setLine1("Unknown");
                orderAddress.setCity("Unknown");
                orderAddress.setPostcode("00000");
                orderAddress.setCountry("Unknown");
                orderContact.setAddress(orderAddress);
                order.setGuestContact(orderContact);
            }

            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Create order with create_order_from_paid transition
            // This will trigger the CreateOrderFromPaid processor
            EntityWithMetadata<Order> orderResponse = entityService.create(order);

            // Mark cart as converted
            entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");

            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(orderResponse.metadata().getState().toUpperCase());

            logger.info("Order {} created from payment {} and cart {}", 
                       order.getOrderId(), request.getPaymentId(), request.getCartId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order details
     * GET /ui/order/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrder(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> order = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (order == null) {
                logger.warn("Order not found for ID: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(order);
        } catch (Exception e) {
            logger.error("Error getting order by ID: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order status (manual transitions)
     * PUT /ui/order/{orderId}/status?transition=TRANSITION_NAME
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderStatus(
            @PathVariable String orderId,
            @RequestParam String transition) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderWithMetadata.entity();
            order.setUpdatedAt(LocalDateTime.now());

            // Update with manual transition
            EntityWithMetadata<Order> response = entityService.update(
                    orderWithMetadata.metadata().getId(), order, transition);

            logger.info("Order {} status updated with transition: {}", orderId, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order status: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods

    private EntityWithMetadata<Payment> getPaymentByBusinessId(String paymentId) {
        ModelSpec modelSpec = new ModelSpec()
                .withName(Payment.ENTITY_NAME)
                .withVersion(Payment.ENTITY_VERSION);
        return entityService.findByBusinessId(modelSpec, paymentId, "paymentId", Payment.class);
    }

    private EntityWithMetadata<Cart> getCartByBusinessId(String cartId) {
        ModelSpec modelSpec = new ModelSpec()
                .withName(Cart.ENTITY_NAME)
                .withVersion(Cart.ENTITY_VERSION);
        return entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
    }

    /**
     * Generate short ULID for order number
     * Simplified implementation - in production use proper ULID library
     */
    private String generateShortULID() {
        return "UL" + System.currentTimeMillis() % 1000000 + 
               UUID.randomUUID().toString().substring(0, 4).toUpperCase();
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

package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Order controller for order management.
 * Provides endpoints for order creation and status retrieval.
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
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            // Validate payment exists and is PAID
            ModelSpec paymentModelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.badRequest().build();
            }

            Payment payment = paymentWithMetadata.entity();

            // Validate payment is PAID
            if (!"PAID".equals(payment.getStatus())) {
                return ResponseEntity.badRequest().build();
            }

            // Validate cart exists and matches payment
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null || !request.getCartId().equals(payment.getCartId())) {
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Create order entity
            Order order = new Order();
            order.setOrderId(UUID.randomUUID().toString());
            order.setOrderNumber(generateShortULID());
            order.setStatus("WAITING_TO_FULFILL");
            
            LocalDateTime now = LocalDateTime.now();
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            // The CreateOrderFromPaid processor will handle the complex logic
            // of snapshotting cart data, decrementing inventory, and creating shipment
            EntityWithMetadata<Order> orderResponse = entityService.create(order);

            // Trigger the order creation workflow
            entityService.update(orderResponse.metadata().getId(), order, "create_order_from_paid");

            // Mark cart as CONVERTED
            cart.setStatus("CONVERTED");
            entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");

            OrderResponse response = new OrderResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(order.getStatus());

            logger.info("Order created: {} from payment: {}", order.getOrderId(), request.getPaymentId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by ID
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
                return ResponseEntity.notFound().build();
            }

            logger.info("Retrieved order: {}", orderId);
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            logger.error("Error getting order by ID: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order status (manual operations)
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
            
            // Update status based on transition
            switch (transition) {
                case "start_picking":
                    order.setStatus("PICKING");
                    break;
                case "ready_to_send":
                    order.setStatus("WAITING_TO_SEND");
                    break;
                case "mark_sent":
                    order.setStatus("SENT");
                    break;
                case "mark_delivered":
                    order.setStatus("DELIVERED");
                    break;
                default:
                    return ResponseEntity.badRequest().build();
            }

            order.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Order> response = entityService.update(
                    orderWithMetadata.metadata().getId(), order, transition);

            logger.info("Order {} status updated to {} via transition {}", orderId, order.getStatus(), transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order status", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate a short ULID-like order number (simplified implementation)
     */
    private String generateShortULID() {
        // Simplified ULID generation - in production use proper ULID library
        return "ORD-" + System.currentTimeMillis() % 1000000;
    }

    // Request/Response DTOs
    @Data
    public static class CreateOrderRequest {
        private String paymentId;
        private String cartId;
    }

    @Data
    public static class OrderResponse {
        private String orderId;
        private String orderNumber;
        private String status;
    }
}

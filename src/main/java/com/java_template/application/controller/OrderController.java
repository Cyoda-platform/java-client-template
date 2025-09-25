package com.java_template.application.controller;

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
 * Order controller for order management
 * Handles order creation from paid payments and order status tracking
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
            // Validate payment exists and is paid
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentResponse = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentResponse == null) {
                logger.warn("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentResponse.entity();
            if (!"PAID".equals(payment.getStatus())) {
                logger.warn("Payment {} is not paid: {}", request.getPaymentId(), payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate cart ID matches
            if (!payment.getCartId().equals(request.getCartId())) {
                logger.warn("Payment cart ID {} does not match request cart ID {}", 
                           payment.getCartId(), request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            // Create order entity
            Order order = new Order();
            order.setOrderId(request.getCartId()); // Use cart ID as order ID for simplicity
            order.setOrderNumber(generateOrderNumber());
            order.setStatus("WAITING_TO_FULFILL");
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // The CreateOrderFromPaid processor will populate the rest of the order data
            EntityWithMetadata<Order> orderResponse = entityService.create(order);

            // Return order summary
            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(order.getStatus());

            logger.info("Order {} created from payment {} for cart {}", 
                       order.getOrderId(), request.getPaymentId(), request.getCartId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating order from payment: {}", request.getPaymentId(), e);
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
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> order = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            logger.debug("Order {} status: {}", orderId, order.entity().getStatus());
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order status (for demo purposes)
     * PUT /ui/order/{orderId}/status
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody OrderStatusUpdateRequest request) {
        try {
            // Get existing order
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderResponse = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderResponse.entity();
            
            // Update order with appropriate transition
            String transition = mapStatusToTransition(request.getStatus());
            if (transition == null) {
                logger.warn("Invalid status transition: {}", request.getStatus());
                return ResponseEntity.badRequest().build();
            }

            EntityWithMetadata<Order> response = entityService.update(orderResponse.metadata().getId(), order, transition);
            logger.info("Order {} status updated to {}", orderId, request.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order status", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate short ULID-style order number
     */
    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Map status to workflow transition
     */
    private String mapStatusToTransition(String status) {
        switch (status) {
            case "PICKING":
                return "start_picking";
            case "WAITING_TO_SEND":
                return "ready_to_send";
            case "SENT":
                return "mark_sent";
            case "DELIVERED":
                return "mark_delivered";
            default:
                return null;
        }
    }

    /**
     * Request and Response DTOs
     */
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

    @Getter
    @Setter
    public static class OrderStatusUpdateRequest {
        private String status;
    }
}

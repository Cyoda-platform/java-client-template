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
 * OrderController - REST endpoints for order management
 * 
 * Handles order creation from paid payments and order status tracking.
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
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentResponse = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentResponse == null) {
                logger.error("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.badRequest().build();
            }

            String paymentState = paymentResponse.metadata().getState();
            if (!"paid".equals(paymentState)) {
                logger.error("Payment {} is not in PAID state. Current state: {}", request.getPaymentId(), paymentState);
                return ResponseEntity.badRequest().build();
            }

            Payment payment = paymentResponse.entity();

            // Validate cart ID matches if provided
            if (request.getCartId() != null && !request.getCartId().equals(payment.getCartId())) {
                logger.error("Cart ID mismatch. Payment cart: {}, Request cart: {}", payment.getCartId(), request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            // Create order entity (the processor will handle the complex logic)
            Order order = new Order();
            order.setOrderId(request.getPaymentId()); // Use payment ID as order ID for simplicity
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Create order (will trigger CreateOrderFromPaidProcessor)
            EntityWithMetadata<Order> orderResponse = entityService.create(order);
            
            Order createdOrder = orderResponse.entity();
            String orderState = orderResponse.metadata().getState();

            OrderResponse response = new OrderResponse();
            response.setOrderId(createdOrder.getOrderId());
            response.setOrderNumber(createdOrder.getOrderNumber());
            response.setStatus(orderState);

            logger.info("Order created: {} with number: {} in state: {}", 
                       createdOrder.getOrderId(), createdOrder.getOrderNumber(), orderState);

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
            EntityWithMetadata<Order> orderResponse = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderResponse == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(orderResponse);
        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order status (manual transitions)
     * POST /ui/order/{orderId}/transition
     */
    @PostMapping("/{orderId}/transition")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody TransitionRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderResponse = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderResponse.entity();
            order.setUpdatedAt(LocalDateTime.now());

            // Update order with specified transition
            EntityWithMetadata<Order> updatedResponse = entityService.update(
                    orderResponse.metadata().getId(), order, request.getTransition());

            logger.info("Order {} transitioned with: {}", orderId, request.getTransition());
            return ResponseEntity.ok(updatedResponse);
        } catch (Exception e) {
            logger.error("Error updating order status: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Request/Response DTOs
     */
    @Getter
    @Setter
    public static class CreateOrderRequest {
        private String paymentId;
        private String cartId; // Optional validation
    }

    @Getter
    @Setter
    public static class OrderResponse {
        private String orderId;
        private String orderNumber;
        private String status;
    }

    @Getter
    @Setter
    public static class TransitionRequest {
        private String transition;
    }
}

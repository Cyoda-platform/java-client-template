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
 * OrderController - REST endpoints for order management
 * 
 * Provides endpoints for:
 * - Order creation from paid payment
 * - Order retrieval and status tracking
 * - Order lifecycle management
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
            // Validate payment exists and is PAID
            Payment payment = getPaymentById(request.getPaymentId());
            if (payment == null) {
                logger.warn("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.badRequest().build();
            }

            if (!"PAID".equals(payment.getStatus())) {
                logger.warn("Payment {} is not PAID: {}", request.getPaymentId(), payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate cart exists
            Cart cart = getCartById(request.getCartId());
            if (cart == null) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            // Create order
            Order order = new Order();
            order.setOrderId(UUID.randomUUID().toString());
            order.setOrderNumber(""); // Will be set by processor
            order.setStatus("WAITING_TO_FULFILL");
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Create order entity (will trigger order creation processor)
            EntityWithMetadata<Order> orderResponse = entityService.create(order);

            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(order.getStatus());

            logger.info("Order {} created from payment {} and cart {}", 
                       order.getOrderId(), request.getPaymentId(), request.getCartId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating order from payment: {}", request.getPaymentId(), e);
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
            EntityWithMetadata<Order> response = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting order by ID: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by technical UUID
     * GET /ui/order/id/{id}
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.getById(id, modelSpec, Order.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting order by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Start picking for order
     * POST /ui/order/{orderId}/start-picking
     */
    @PostMapping("/{orderId}/start-picking")
    public ResponseEntity<EntityWithMetadata<Order>> startPicking(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderResponse = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderResponse.entity();

            // Update order to PICKING
            EntityWithMetadata<Order> response = entityService.update(
                    orderResponse.metadata().getId(), order, "start_picking");
            
            logger.info("Started picking for order: {}", orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error starting picking for order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mark order ready to send
     * POST /ui/order/{orderId}/ready-to-send
     */
    @PostMapping("/{orderId}/ready-to-send")
    public ResponseEntity<EntityWithMetadata<Order>> readyToSend(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderResponse = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderResponse.entity();

            // Update order to WAITING_TO_SEND
            EntityWithMetadata<Order> response = entityService.update(
                    orderResponse.metadata().getId(), order, "ready_to_send");
            
            logger.info("Order {} ready to send", orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error marking order ready to send: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mark order as sent
     * POST /ui/order/{orderId}/mark-sent
     */
    @PostMapping("/{orderId}/mark-sent")
    public ResponseEntity<EntityWithMetadata<Order>> markSent(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderResponse = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderResponse.entity();

            // Update order to SENT
            EntityWithMetadata<Order> response = entityService.update(
                    orderResponse.metadata().getId(), order, "mark_sent");
            
            logger.info("Order {} marked as sent", orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error marking order as sent: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mark order as delivered
     * POST /ui/order/{orderId}/mark-delivered
     */
    @PostMapping("/{orderId}/mark-delivered")
    public ResponseEntity<EntityWithMetadata<Order>> markDelivered(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderResponse = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderResponse.entity();

            // Update order to DELIVERED
            EntityWithMetadata<Order> response = entityService.update(
                    orderResponse.metadata().getId(), order, "mark_delivered");
            
            logger.info("Order {} marked as delivered", orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error marking order as delivered: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payment by ID
     */
    private Payment getPaymentById(String paymentId) {
        try {
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentResponse = entityService.findByBusinessId(
                    paymentModelSpec, paymentId, "paymentId", Payment.class);
            return paymentResponse != null ? paymentResponse.entity() : null;
        } catch (Exception e) {
            logger.error("Error getting payment by ID: {}", paymentId, e);
            return null;
        }
    }

    /**
     * Get cart by ID
     */
    private Cart getCartById(String cartId) {
        try {
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);
            return cartResponse != null ? cartResponse.entity() : null;
        } catch (Exception e) {
            logger.error("Error getting cart by ID: {}", cartId, e);
            return null;
        }
    }

    /**
     * Request DTO for creating order
     */
    @Getter
    @Setter
    public static class OrderCreateRequest {
        private String paymentId;
        private String cartId;
    }

    /**
     * Response DTO for order creation
     */
    @Getter
    @Setter
    public static class OrderCreateResponse {
        private String orderId;
        private String orderNumber;
        private String status;
    }
}

package com.java_template.application.controller;

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
 * OrderController - Manages order creation and tracking.
 * 
 * Endpoints:
 * - POST /ui/order/create - Create order from paid cart
 * - GET /ui/order/{orderId} - Get order details for confirmation/tracking
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
    public ResponseEntity<OrderCreateResponse> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            // Validate payment is PAID
            ModelSpec paymentModelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);
            
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.badRequest().build();
            }

            String paymentState = paymentWithMetadata.metadata().getState();
            if (!"paid".equals(paymentState)) {
                return ResponseEntity.badRequest().build();
            }

            // Create order entity with minimal data
            // The processor will handle the complex logic of snapshotting cart data
            Order order = new Order();
            order.setOrderId(UUID.randomUUID().toString());
            
            // Pass cartId and paymentId through the order fields temporarily
            // The processor will extract these and populate the order properly
            order.setOrderNumber(request.getPaymentId()); // Temporary - processor will replace with ULID
            
            LocalDateTime now = LocalDateTime.now();
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            // Create order with CREATE_ORDER transition
            // The OrderCreateFromPaidProcessor will handle the complex business logic
            EntityWithMetadata<Order> orderWithMetadata = entityService.create(order);
            
            Order createdOrder = orderWithMetadata.entity();
            String orderState = orderWithMetadata.metadata().getState();

            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(createdOrder.getOrderId());
            response.setOrderNumber(createdOrder.getOrderNumber());
            response.setStatus(orderState.toUpperCase());
            response.setLines(createdOrder.getLines());
            response.setTotals(createdOrder.getTotals());
            response.setGuestContact(createdOrder.getGuestContact());
            response.setCreatedAt(createdOrder.getCreatedAt());

            logger.info("Order created: {} with order number: {}", 
                       createdOrder.getOrderId(), createdOrder.getOrderNumber());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error creating order for payment: {}", request.getPaymentId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order details for confirmation/tracking
     * GET /ui/order/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailsResponse> getOrder(@PathVariable String orderId) {
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
            String orderState = orderWithMetadata.metadata().getState();

            OrderDetailsResponse response = new OrderDetailsResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(orderState.toUpperCase());
            response.setLines(order.getLines());
            response.setTotals(order.getTotals());
            response.setGuestContact(order.getGuestContact());
            response.setCreatedAt(order.getCreatedAt());
            response.setUpdatedAt(order.getUpdatedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Request DTO for creating order
     */
    @Data
    public static class CreateOrderRequest {
        private String paymentId;
        private String cartId;
    }

    /**
     * Response DTO for order creation
     */
    @Data
    public static class OrderCreateResponse {
        private String orderId;
        private String orderNumber;
        private String status;
        private java.util.List<Order.OrderLine> lines;
        private Order.OrderTotals totals;
        private Order.OrderGuestContact guestContact;
        private LocalDateTime createdAt;
    }

    /**
     * Response DTO for order details
     */
    @Data
    public static class OrderDetailsResponse {
        private String orderId;
        private String orderNumber;
        private String status;
        private java.util.List<Order.OrderLine> lines;
        private Order.OrderTotals totals;
        private Order.OrderGuestContact guestContact;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}

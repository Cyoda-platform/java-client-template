package com.java_template.application.controller;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.CyodaExceptionUtil;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Order endpoints including creation from paid payments and order status retrieval.
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
            // Verify payment is PAID
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            String paymentState = paymentWithMetadata.metadata().getState();
            if (!"PAID".equals(paymentState)) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        String.format("Payment is not in PAID state: %s", paymentState)
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Create order
            Order order = new Order();
            order.setOrderId(UUID.randomUUID().toString());
            order.setOrderNumber(generateOrderNumber());
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // The processor will populate lines, totals, and guestContact from cart
            // and create the shipment
            EntityWithMetadata<Order> response = entityService.create(order);
            logger.info("Order created with ID: {}", response.metadata().getId());

            OrderCreateResponse orderResponse = new OrderCreateResponse();
            orderResponse.setOrderId(order.getOrderId());
            orderResponse.setOrderNumber(order.getOrderNumber());
            orderResponse.setStatus(response.metadata().getState());

            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(order.getOrderId())
                    .toUri();

            return ResponseEntity.created(location).body(orderResponse);
        } catch (Exception e) {
            logger.error("Failed to create order: {}", e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to create order: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            EntityWithMetadata<Order> response = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            if (CyodaExceptionUtil.isNotFound(e)) {
                return ResponseEntity.notFound().build();
            }
            logger.error("Failed to get order {}: {}", orderId, e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve order: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Generate a short ULID-like order number
     */
    private String generateOrderNumber() {
        // Simple implementation: use first 12 chars of UUID
        return UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

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


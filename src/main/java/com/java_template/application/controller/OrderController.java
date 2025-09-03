package com.java_template.application.controller;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ui/order")
@CrossOrigin(origins = "*")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final EntityService entityService;

    public OrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/create")
    public ResponseEntity<Object> createOrder(@RequestBody Map<String, Object> request) {
        try {
            String paymentId = (String) request.get("paymentId");
            String cartId = (String) request.get("cartId");

            logger.info("Creating order from payment: {} and cart: {}", paymentId, cartId);

            if (paymentId == null || cartId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "INVALID_REQUEST", "message", "Both paymentId and cartId are required"));
            }

            // Validate payment exists and is PAID
            var paymentResponses = entityService.findByField(
                    Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, "paymentId", paymentId);

            if (paymentResponses.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "PAYMENT_NOT_FOUND", "message", "Payment not found with ID: " + paymentId));
            }

            String paymentState = paymentResponses.get(0).getMetadata().getState();
            if (!"paid".equals(paymentState)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "PAYMENT_NOT_PAID", "message", "Payment must be PAID to create order"));
            }

            // Create order entity
            Order order = new Order();
            order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Create payload with payment and cart IDs for processor
            Map<String, Object> payload = new HashMap<>();
            payload.put("paymentId", paymentId);
            payload.put("cartId", cartId);

            // Save order with CREATE_ORDER_FROM_PAID transition
            // The processor will handle the complex logic of creating the order from paid payment
            var savedOrderResponse = entityService.save(order);
            UUID orderEntityId = savedOrderResponse.getMetadata().getId();

            // Update with transition to trigger processor
            var updatedOrderResponse = entityService.update(orderEntityId, order, "transition_to_picking");
            Order createdOrder = updatedOrderResponse.getData();
            String orderState = updatedOrderResponse.getMetadata().getState();

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", createdOrder.getOrderId());
            response.put("orderNumber", createdOrder.getOrderNumber());
            response.put("status", orderState.toUpperCase());
            response.put("message", "Order created successfully");

            logger.info("Order created successfully with ID: {}", createdOrder.getOrderId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error creating order: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "ORDER_CREATION_ERROR", "message", "Failed to create order: " + e.getMessage()));
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Object> getOrder(@PathVariable String orderId) {
        try {
            logger.info("Getting order details: {}", orderId);

            // Find order
            var orderResponses = entityService.findByField(
                    Order.class, Order.ENTITY_NAME, Order.ENTITY_VERSION, "orderId", orderId);

            if (orderResponses.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderResponses.get(0).getData();
            String orderState = orderResponses.get(0).getMetadata().getState();

            // Create response with order details and current state
            Map<String, Object> response = new HashMap<>();
            response.put("orderId", order.getOrderId());
            response.put("orderNumber", order.getOrderNumber());
            response.put("status", orderState.toUpperCase());
            response.put("lines", order.getLines());
            response.put("totals", order.getTotals());
            response.put("guestContact", order.getGuestContact());
            response.put("createdAt", order.getCreatedAt());
            response.put("updatedAt", order.getUpdatedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "ORDER_ERROR", "message", "Failed to get order: " + e.getMessage()));
        }
    }
}

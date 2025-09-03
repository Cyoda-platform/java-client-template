package com.java_template.application.controller;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class CreateOrderRequest {
    private String paymentId;
    private String cartId;

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getCartId() { return cartId; }
    public void setCartId(String cartId) { this.cartId = cartId; }
}

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
    public ResponseEntity<Object> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            String paymentId = request.getPaymentId();
            String cartId = request.getCartId();

            logger.info("Creating order from payment: {} and cart: {}", paymentId, cartId);

            if (paymentId == null || cartId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "INVALID_REQUEST", "message", "Both paymentId and cartId are required"));
            }

            // Validate payment exists and is PAID
            EntityResponse<Payment> paymentResponse = entityService.findByBusinessId(Payment.class, paymentId, "paymentId");

            if (paymentResponse == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "PAYMENT_NOT_FOUND", "message", "Payment not found with ID: " + paymentId));
            }

            String paymentState = paymentResponse.getMetadata().getState();
            if (!"paid".equals(paymentState)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "PAYMENT_NOT_PAID", "message", "Payment must be PAID to create order"));
            }

            // Create order entity with payment and cart IDs
            Order order = new Order();
            order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            order.setPaymentId(paymentId);  // Temporary field for processor
            order.setCartId(cartId);        // Temporary field for processor
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Save order - the entity itself contains all the data needed
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
            EntityResponse<Order> orderResponse = entityService.findByBusinessId(Order.class, orderId, "orderId");

            if (orderResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderResponse.getData();
            String orderState = orderResponse.getMetadata().getState();

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

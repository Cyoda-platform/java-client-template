package com.java_template.application.controller;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/ui/order")
@CrossOrigin(origins = "*")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    
    @Autowired
    private EntityService entityService;

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestParam(required = true) String transition,
            @RequestBody CreateOrderRequest request) {
        
        logger.info("Creating order from payment: {}, cart: {}, transition: {}", 
                   request.getPaymentId(), request.getCartId(), transition);

        try {
            // Validate payment is PAID
            Optional<Payment> paymentOpt = findPaymentByPaymentId(request.getPaymentId());
            if (paymentOpt.isEmpty()) {
                logger.warn("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.badRequest().build();
            }

            Payment payment = paymentOpt.get();
            String paymentState = getPaymentState(request.getPaymentId());
            
            if (!"PAID".equals(paymentState)) {
                logger.warn("Payment {} is not in PAID state: {}", request.getPaymentId(), paymentState);
                return ResponseEntity.badRequest().build();
            }

            // Find cart
            Optional<Cart> cartOpt = findCartByCartId(request.getCartId());
            if (cartOpt.isEmpty()) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartOpt.get();

            // Create order
            Order order = new Order();
            order.setOrderId("order-" + UUID.randomUUID().toString());
            order.setOrderNumber(generateShortULID());
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // This will be populated by the OrderCreateFromPaidProcessor
            // For now, we'll set basic structure
            order.setLines(List.of());
            Order.OrderTotals totals = new Order.OrderTotals();
            totals.setItems(0);
            totals.setGrand(0.0);
            order.setTotals(totals);

            // Save order with transition - the processor will handle the business logic
            EntityResponse<Order> orderResponse = entityService.save(order);

            // Build response
            Map<String, Object> response = new HashMap<>();
            Order savedOrder = orderResponse.getData();
            String orderState = orderResponse.getMetadata().getState();
            
            response.put("orderId", savedOrder.getOrderId());
            response.put("orderNumber", savedOrder.getOrderNumber());
            response.put("status", orderState != null ? orderState : "WAITING_TO_FULFILL");
            response.put("lines", savedOrder.getLines());
            response.put("totals", savedOrder.getTotals());
            response.put("guestContact", savedOrder.getGuestContact());
            response.put("createdAt", savedOrder.getCreatedAt());
            response.put("updatedAt", savedOrder.getUpdatedAt());

            logger.info("Created order: {} from payment: {}", savedOrder.getOrderNumber(), request.getPaymentId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to create order from payment {}: {}", request.getPaymentId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String orderId) {
        logger.info("Getting order: {}", orderId);

        try {
            Optional<Order> orderOpt = findOrderByOrderId(orderId);
            
            if (orderOpt.isEmpty()) {
                logger.warn("Order not found: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            Order order = orderOpt.get();
            String orderState = getOrderState(orderId);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("orderId", order.getOrderId());
            response.put("orderNumber", order.getOrderNumber());
            response.put("status", orderState != null ? orderState : "UNKNOWN");
            response.put("lines", order.getLines());
            response.put("totals", order.getTotals());
            response.put("guestContact", order.getGuestContact());
            response.put("createdAt", order.getCreatedAt());
            response.put("updatedAt", order.getUpdatedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Optional<Payment> findPaymentByPaymentId(String paymentId) {
        try {
            Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(paymentIdCondition));

            Optional<EntityResponse<Payment>> paymentResponse = entityService.getFirstItemByCondition(
                Payment.class,
                Payment.ENTITY_NAME,
                Payment.ENTITY_VERSION,
                condition,
                true
            );

            return paymentResponse.map(EntityResponse::getData);
        } catch (Exception e) {
            logger.error("Failed to find payment by ID {}: {}", paymentId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Cart> findCartByCartId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class,
                Cart.ENTITY_NAME,
                Cart.ENTITY_VERSION,
                condition,
                true
            );

            return cartResponse.map(EntityResponse::getData);
        } catch (Exception e) {
            logger.error("Failed to find cart by ID {}: {}", cartId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Order> findOrderByOrderId(String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(orderIdCondition));

            Optional<EntityResponse<Order>> orderResponse = entityService.getFirstItemByCondition(
                Order.class,
                Order.ENTITY_NAME,
                Order.ENTITY_VERSION,
                condition,
                true
            );

            return orderResponse.map(EntityResponse::getData);
        } catch (Exception e) {
            logger.error("Failed to find order by ID {}: {}", orderId, e.getMessage());
            return Optional.empty();
        }
    }

    private String getPaymentState(String paymentId) {
        try {
            Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(paymentIdCondition));

            Optional<EntityResponse<Payment>> paymentResponse = entityService.getFirstItemByCondition(
                Payment.class,
                Payment.ENTITY_NAME,
                Payment.ENTITY_VERSION,
                condition,
                true
            );

            return paymentResponse.map(response -> response.getMetadata().getState()).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to get payment state for {}: {}", paymentId, e.getMessage());
            return null;
        }
    }

    private String getOrderState(String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(orderIdCondition));

            Optional<EntityResponse<Order>> orderResponse = entityService.getFirstItemByCondition(
                Order.class,
                Order.ENTITY_NAME,
                Order.ENTITY_VERSION,
                condition,
                true
            );

            return orderResponse.map(response -> response.getMetadata().getState()).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to get order state for {}: {}", orderId, e.getMessage());
            return null;
        }
    }

    private String generateShortULID() {
        // Simple ULID-like generation (in practice, use a proper ULID library)
        return UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }

    // Request DTOs
    public static class CreateOrderRequest {
        private String paymentId;
        private String cartId;

        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        public String getCartId() { return cartId; }
        public void setCartId(String cartId) { this.cartId = cartId; }
    }
}

package com.java_template.application.controller;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, String> request) {
        try {
            String paymentId = request.get("paymentId");
            String cartId = request.get("cartId");
            
            logger.info("Creating order from payment: {} and cart: {}", paymentId, cartId);

            if (paymentId == null || cartId == null) {
                logger.warn("Payment ID and Cart ID are required");
                return ResponseEntity.badRequest().build();
            }

            // Validate payment is PAID
            Payment payment = getPaymentByPaymentId(paymentId);
            if (payment == null) {
                logger.warn("Payment not found: {}", paymentId);
                return ResponseEntity.badRequest().build();
            }

            // Check payment state
            String paymentState = getPaymentState(paymentId);
            if (!"PAID".equals(paymentState)) {
                logger.warn("Payment {} is not in PAID state: {}", paymentId, paymentState);
                return ResponseEntity.badRequest().build();
            }

            // Create order entity
            Order order = new Order();
            order.setOrderId("order-" + UUID.randomUUID().toString());
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());

            // For this demo, we'll create a basic order structure
            // In a real implementation, the OrderCreateFromPaidProcessor would handle
            // the complex logic of snapshotting cart data, decrementing stock, etc.

            // CRITICAL: Pass order entity directly - it IS the payload
            EntityResponse<Order> response = entityService.save(order);
            
            String orderState = response.getMetadata().getState();
            
            // Build response
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("orderId", order.getOrderId());
            responseMap.put("orderNumber", order.getOrderNumber());
            responseMap.put("status", orderState);
            responseMap.put("createdAt", order.getCreatedAt());
            responseMap.put("updatedAt", order.getUpdatedAt());

            logger.info("Order created: {} with status: {}", order.getOrderId(), orderState);
            return ResponseEntity.ok(responseMap);

        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String orderId) {
        try {
            logger.info("Getting order: {}", orderId);

            // Search for order by orderId
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", orderIdCondition);

            Optional<EntityResponse<Order>> orderResponse = entityService.getFirstItemByCondition(
                Order.class, Order.ENTITY_NAME, Order.ENTITY_VERSION, condition, true);

            if (orderResponse.isPresent()) {
                Order order = orderResponse.get().getData();
                String status = orderResponse.get().getMetadata().getState();
                
                // Build response
                Map<String, Object> response = new HashMap<>();
                response.put("orderId", order.getOrderId());
                response.put("orderNumber", order.getOrderNumber());
                response.put("status", status);
                response.put("lines", order.getLines());
                response.put("totals", order.getTotals());
                response.put("guestContact", order.getGuestContact());
                response.put("createdAt", order.getCreatedAt());
                response.put("updatedAt", order.getUpdatedAt());

                logger.info("Found order: {} with status: {}", orderId, status);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Order not found: {}", orderId);
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<EntityResponse<Order>> updateOrder(
            @PathVariable String orderId, 
            @RequestBody Order order,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating order: {} with transition: {}", orderId, transition);
            
            // Find order by orderId to get technical ID
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", orderIdCondition);

            Optional<EntityResponse<Order>> existingResponse = entityService.getFirstItemByCondition(
                Order.class, Order.ENTITY_NAME, Order.ENTITY_VERSION, condition, true);

            if (existingResponse.isPresent()) {
                // CRITICAL: Pass order entity directly - no payload manipulation needed
                EntityResponse<Order> response = entityService.update(
                    existingResponse.get().getMetadata().getId(), order, transition);
                logger.info("Order updated: {}", orderId);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Order not found for update: {}", orderId);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error updating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private Payment getPaymentByPaymentId(String paymentId) {
        try {
            Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", paymentIdCondition);

            Optional<EntityResponse<Payment>> paymentResponse = entityService.getFirstItemByCondition(
                Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, condition, true);

            return paymentResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error getting payment by paymentId: {}", paymentId, e);
            return null;
        }
    }

    private String getPaymentState(String paymentId) {
        try {
            Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", paymentIdCondition);

            Optional<EntityResponse<Payment>> paymentResponse = entityService.getFirstItemByCondition(
                Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, condition, true);

            return paymentResponse.map(response -> response.getMetadata().getState()).orElse(null);
        } catch (Exception e) {
            logger.error("Error getting payment state: {}", paymentId, e);
            return null;
        }
    }
}

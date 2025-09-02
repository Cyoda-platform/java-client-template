package com.java_template.application.controller.order.version_1;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OrderController handles order management operations.
 */
@RestController
@RequestMapping("/ui/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final EntityService entityService;

    public OrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create order from cart and payment
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Map<String, Object> requestBody) {
        logger.info("Creating order");

        try {
            String cartId = (String) requestBody.get("cartId");
            String paymentId = (String) requestBody.get("paymentId");
            
            if (cartId == null || cartId.trim().isEmpty()) {
                logger.warn("Cart ID is required for order creation");
                return ResponseEntity.badRequest().build();
            }
            
            if (paymentId == null || paymentId.trim().isEmpty()) {
                logger.warn("Payment ID is required for order creation");
                return ResponseEntity.badRequest().build();
            }

            // Create new order
            String orderId = "order_" + UUID.randomUUID().toString().replace("-", "");
            String orderNumber = UUID.randomUUID().toString().substring(0, 10).toUpperCase();
            
            Order order = new Order();
            order.setOrderId(orderId);
            order.setOrderNumber(orderNumber);
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Prepare request data for processor
            Map<String, Object> processorData = new HashMap<>();
            processorData.put("cartId", cartId);
            processorData.put("paymentId", paymentId);

            // Save order
            EntityResponse<Order> response = entityService.save(order);
            Order savedOrder = response.getData();

            logger.info("Order created successfully: orderId={}, orderNumber={}", 
                savedOrder.getOrderId(), savedOrder.getOrderNumber());
            return ResponseEntity.ok(savedOrder);

        } catch (Exception e) {
            logger.error("Failed to create order: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Start picking process for order
     */
    @PostMapping("/{orderId}/start-picking")
    public ResponseEntity<Order> startPicking(
            @PathVariable String orderId,
            @RequestParam String transition) {

        logger.info("Starting picking for order: orderId={}, transition={}", orderId, transition);

        if (!"START_PICKING".equals(transition)) {
            logger.warn("Invalid transition for start picking: {}", transition);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Get existing order
            EntityResponse<Order> orderResponse = entityService.findByBusinessId(Order.class, orderId);
            Order order = orderResponse.getData();
            
            if (order == null) {
                logger.warn("Order not found: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            // Update order with START_PICKING transition
            EntityResponse<Order> updatedResponse = entityService.update(
                orderResponse.getId(),
                order,
                "START_PICKING"
            );
            Order updatedOrder = updatedResponse.getData();

            logger.info("Picking started for order: orderId={}", updatedOrder.getOrderId());
            return ResponseEntity.ok(updatedOrder);

        } catch (Exception e) {
            logger.error("Failed to start picking for order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mark order as ready to send
     */
    @PostMapping("/{orderId}/ready-to-send")
    public ResponseEntity<Order> readyToSend(
            @PathVariable String orderId,
            @RequestParam String transition) {

        logger.info("Marking order ready to send: orderId={}, transition={}", orderId, transition);

        if (!"READY_TO_SEND".equals(transition)) {
            logger.warn("Invalid transition for ready to send: {}", transition);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Get existing order
            EntityResponse<Order> orderResponse = entityService.findByBusinessId(Order.class, orderId);
            Order order = orderResponse.getData();
            
            if (order == null) {
                logger.warn("Order not found: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            // Update order with READY_TO_SEND transition
            EntityResponse<Order> updatedResponse = entityService.update(
                orderResponse.getId(),
                order,
                "READY_TO_SEND"
            );
            Order updatedOrder = updatedResponse.getData();

            logger.info("Order marked ready to send: orderId={}", updatedOrder.getOrderId());
            return ResponseEntity.ok(updatedOrder);

        } catch (Exception e) {
            logger.error("Failed to mark order ready to send {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mark order as sent
     */
    @PostMapping("/{orderId}/mark-sent")
    public ResponseEntity<Order> markSent(
            @PathVariable String orderId,
            @RequestParam String transition) {

        logger.info("Marking order as sent: orderId={}, transition={}", orderId, transition);

        if (!"MARK_SENT".equals(transition)) {
            logger.warn("Invalid transition for mark sent: {}", transition);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Get existing order
            EntityResponse<Order> orderResponse = entityService.findByBusinessId(Order.class, orderId);
            Order order = orderResponse.getData();
            
            if (order == null) {
                logger.warn("Order not found: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            // Update order with MARK_SENT transition
            EntityResponse<Order> updatedResponse = entityService.update(
                orderResponse.getId(),
                order,
                "MARK_SENT"
            );
            Order updatedOrder = updatedResponse.getData();

            logger.info("Order marked as sent: orderId={}", updatedOrder.getOrderId());
            return ResponseEntity.ok(updatedOrder);

        } catch (Exception e) {
            logger.error("Failed to mark order as sent {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mark order as delivered
     */
    @PostMapping("/{orderId}/mark-delivered")
    public ResponseEntity<Order> markDelivered(
            @PathVariable String orderId,
            @RequestParam String transition) {

        logger.info("Marking order as delivered: orderId={}, transition={}", orderId, transition);

        if (!"MARK_DELIVERED".equals(transition)) {
            logger.warn("Invalid transition for mark delivered: {}", transition);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Get existing order
            EntityResponse<Order> orderResponse = entityService.findByBusinessId(Order.class, orderId);
            Order order = orderResponse.getData();
            
            if (order == null) {
                logger.warn("Order not found: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            // Update order with MARK_DELIVERED transition
            EntityResponse<Order> updatedResponse = entityService.update(
                orderResponse.getId(),
                order,
                "MARK_DELIVERED"
            );
            Order updatedOrder = updatedResponse.getData();

            logger.info("Order marked as delivered: orderId={}", updatedOrder.getOrderId());
            return ResponseEntity.ok(updatedOrder);

        } catch (Exception e) {
            logger.error("Failed to mark order as delivered {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order details
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        logger.info("Getting order details: orderId={}", orderId);

        try {
            EntityResponse<Order> response = entityService.findByBusinessId(Order.class, orderId);
            Order order = response.getData();
            
            if (order == null) {
                logger.warn("Order not found: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            logger.info("Found order: orderId={}, orderNumber={}, totalItems={}", 
                order.getOrderId(), order.getOrderNumber(), 
                order.getTotals() != null ? order.getTotals().getItems() : 0);
            return ResponseEntity.ok(order);

        } catch (Exception e) {
            logger.error("Failed to get order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Search orders by guest contact email
     */
    @GetMapping
    public ResponseEntity<List<EntityResponse<Order>>> searchOrders(
            @RequestParam(required = false) String guestEmail) {

        logger.info("Searching orders: guestEmail={}", guestEmail);

        try {
            List<EntityResponse<Order>> orders;
            
            if (guestEmail != null && !guestEmail.trim().isEmpty()) {
                SearchConditionRequest condition = new SearchConditionRequest();
                List<Condition> conditions = new ArrayList<>();
                conditions.add(Condition.of("guestContact.email", "equals", guestEmail));
                condition.setConditions(conditions);
                orders = entityService.getItemsByCondition(Order.class, condition, false);
            } else {
                orders = entityService.getItems(Order.class, 50, 0, null);
            }

            logger.info("Found {} orders", orders.size());
            return ResponseEntity.ok(orders);

        } catch (Exception e) {
            logger.error("Failed to search orders: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

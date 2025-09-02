package com.java_template.application.controller;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/ui/order")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final EntityService entityService;

    public OrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> request) {
        try {
            String paymentId = (String) request.get("paymentId");
            String cartId = (String) request.get("cartId");
            
            logger.info("Creating order from payment: {} and cart: {}", paymentId, cartId);

            // Validate payment is paid
            Payment payment = getPaymentByPaymentId(paymentId);
            if (payment == null) {
                return ResponseEntity.badRequest().build();
            }

            // Validate cart is converted
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                return ResponseEntity.badRequest().build();
            }

            // Create order
            Order order = new Order();
            order.setOrderId("ord_" + UUID.randomUUID().toString().substring(0, 8));
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());

            // Save order - this will trigger create_order_from_paid transition
            EntityResponse<Order> savedOrder = entityService.save(order);

            Map<String, Object> response = convertToOrderResponse(savedOrder.getData(), savedOrder.getMetadata().getState());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String orderId) {
        try {
            logger.info("Getting order: {}", orderId);

            Order order = getOrderByOrderId(orderId);
            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            // Get order state
            String state = getOrderState(orderId);
            
            Map<String, Object> response = convertToOrderResponse(order, state);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{orderId}/start-picking")
    public ResponseEntity<Map<String, Object>> startPicking(@PathVariable String orderId) {
        try {
            logger.info("Starting picking for order: {}", orderId);

            Order order = getOrderByOrderId(orderId);
            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            UUID orderEntityId = getOrderEntityId(orderId);
            if (orderEntityId == null) {
                return ResponseEntity.notFound().build();
            }

            EntityResponse<Order> updatedOrder = entityService.update(orderEntityId, order, "start_picking");

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("status", updatedOrder.getMetadata().getState());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error starting picking for order: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{orderId}/ready-to-send")
    public ResponseEntity<Map<String, Object>> readyToSend(@PathVariable String orderId) {
        try {
            logger.info("Marking order ready to send: {}", orderId);

            Order order = getOrderByOrderId(orderId);
            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            UUID orderEntityId = getOrderEntityId(orderId);
            if (orderEntityId == null) {
                return ResponseEntity.notFound().build();
            }

            EntityResponse<Order> updatedOrder = entityService.update(orderEntityId, order, "ready_to_send");

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("status", updatedOrder.getMetadata().getState());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error marking order ready to send: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{orderId}/mark-sent")
    public ResponseEntity<Map<String, Object>> markSent(@PathVariable String orderId) {
        try {
            logger.info("Marking order as sent: {}", orderId);

            Order order = getOrderByOrderId(orderId);
            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            UUID orderEntityId = getOrderEntityId(orderId);
            if (orderEntityId == null) {
                return ResponseEntity.notFound().build();
            }

            EntityResponse<Order> updatedOrder = entityService.update(orderEntityId, order, "mark_sent");

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("status", updatedOrder.getMetadata().getState());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error marking order as sent: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{orderId}/mark-delivered")
    public ResponseEntity<Map<String, Object>> markDelivered(@PathVariable String orderId) {
        try {
            logger.info("Marking order as delivered: {}", orderId);

            Order order = getOrderByOrderId(orderId);
            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            UUID orderEntityId = getOrderEntityId(orderId);
            if (orderEntityId == null) {
                return ResponseEntity.notFound().build();
            }

            EntityResponse<Order> updatedOrder = entityService.update(orderEntityId, order, "mark_delivered");

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("status", updatedOrder.getMetadata().getState());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error marking order as delivered: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Payment getPaymentByPaymentId(String paymentId) {
        try {
            Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("simple");
            condition.setConditions(List.of(paymentIdCondition));

            Optional<EntityResponse<Payment>> paymentResponse = entityService.getFirstItemByCondition(
                Payment.class, 
                Payment.ENTITY_NAME, 
                Payment.ENTITY_VERSION, 
                condition, 
                true
            );

            return paymentResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error fetching payment by ID: {}", paymentId, e);
            return null;
        }
    }

    private Cart getCartByCartId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("simple");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, 
                Cart.ENTITY_NAME, 
                Cart.ENTITY_VERSION, 
                condition, 
                true
            );

            return cartResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error fetching cart by ID: {}", cartId, e);
            return null;
        }
    }

    private Order getOrderByOrderId(String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("simple");
            condition.setConditions(List.of(orderIdCondition));

            Optional<EntityResponse<Order>> orderResponse = entityService.getFirstItemByCondition(
                Order.class, 
                Order.ENTITY_NAME, 
                Order.ENTITY_VERSION, 
                condition, 
                true
            );

            return orderResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error fetching order by ID: {}", orderId, e);
            return null;
        }
    }

    private UUID getOrderEntityId(String orderId) {
        // TODO: Implement proper entity ID lookup
        return null;
    }

    private String getOrderState(String orderId) {
        // TODO: Implement proper state lookup
        return "waiting_to_fulfill";
    }

    private Map<String, Object> convertToOrderResponse(Order order, String status) {
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("orderNumber", order.getOrderNumber());
        response.put("status", status);
        response.put("lines", order.getLines());
        response.put("totals", order.getTotals());
        response.put("guestContact", order.getGuestContact());
        response.put("createdAt", order.getCreatedAt());
        response.put("updatedAt", order.getUpdatedAt());
        return response;
    }
}

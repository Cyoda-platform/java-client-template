package com.java_template.application.controller;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@RestController
@RequestMapping("/ui/order")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping("/create")
    public ResponseEntity<Order> createOrder(
            @RequestParam(required = false) String transitionName,
            @RequestBody Map<String, Object> request) {

        logger.info("Creating order from paid cart");

        try {
            String paymentId = (String) request.get("paymentId");
            String cartId = (String) request.get("cartId");

            if (paymentId == null || cartId == null) {
                return ResponseEntity.badRequest().build();
            }

            // Validate payment is paid
            Payment payment = getPaymentByPaymentId(paymentId);
            if (payment == null) {
                return ResponseEntity.badRequest().build();
            }

            // Get cart
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                return ResponseEntity.badRequest().build();
            }

            // Create order entity
            Order order = new Order();
            order.setOrderId(UUID.randomUUID().toString());
            // Note: orderNumber and other fields will be set by the processor

            // Save order with transition - the processor will handle the business logic
            String transition = transitionName != null ? transitionName : "CREATE_ORDER_FROM_PAID";
            EntityResponse<Order> orderResponse = entityService.save(order);

            return ResponseEntity.ok(orderResponse.getData());

        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        logger.info("Getting order: {}", orderId);

        try {
            Order order = getOrderByOrderId(orderId);
            if (order != null) {
                return ResponseEntity.ok(order);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Payment getPaymentByPaymentId(String paymentId) {
        try {
            Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(paymentIdCondition));

            Optional<EntityResponse<Payment>> paymentResponse = entityService.getFirstItemByCondition(
                Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, condition, true);

            return paymentResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving payment by paymentId: {}", paymentId, e);
            return null;
        }
    }

    private Cart getCartByCartId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);

            return cartResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving cart by cartId: {}", cartId, e);
            return null;
        }
    }

    private Order getOrderByOrderId(String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(orderIdCondition));

            Optional<EntityResponse<Order>> orderResponse = entityService.getFirstItemByCondition(
                Order.class, Order.ENTITY_NAME, Order.ENTITY_VERSION, condition, true);

            return orderResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving order by orderId: {}", orderId, e);
            return null;
        }
    }
}

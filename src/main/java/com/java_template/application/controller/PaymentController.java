package com.java_template.application.controller;

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
@RequestMapping("/ui/payment")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping("/start")
    public ResponseEntity<Payment> startPayment(@RequestBody Map<String, Object> request) {
        logger.info("Starting payment process");

        try {
            String cartId = (String) request.get("cartId");
            if (cartId == null || cartId.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Get cart to validate and get amount
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                return ResponseEntity.badRequest().build();
            }

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(cartId);
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY"); // Will be set by processor

            // Save payment with transition
            EntityResponse<Payment> paymentResponse = entityService.save(payment);

            return ResponseEntity.ok(paymentResponse.getData());

        } catch (Exception e) {
            logger.error("Error starting payment", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<Payment> getPayment(@PathVariable String paymentId) {
        logger.info("Getting payment: {}", paymentId);

        try {
            Payment payment = getPaymentByPaymentId(paymentId);
            if (payment != null) {
                return ResponseEntity.ok(payment);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error getting payment: {}", paymentId, e);
            return ResponseEntity.internalServerError().build();
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
}

package com.java_template.application.controller;

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
@RequestMapping("/ui/payment")
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    
    @Autowired
    private EntityService entityService;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startPayment(
            @RequestParam(required = false) String transition,
            @RequestBody StartPaymentRequest request) {
        
        logger.info("Starting payment for cart: {}, transition: {}", request.getCartId(), transition);

        try {
            // Find cart to get amount
            Optional<Cart> cartOpt = findCartByCartId(request.getCartId());
            if (cartOpt.isEmpty()) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartOpt.get();

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId("payment-" + UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            // Save payment with transition
            EntityResponse<Payment> paymentResponse = entityService.save(payment);

            // Build response
            Map<String, Object> response = new HashMap<>();
            Payment savedPayment = paymentResponse.getData();
            response.put("paymentId", savedPayment.getPaymentId());
            response.put("cartId", savedPayment.getCartId());
            response.put("amount", savedPayment.getAmount());
            response.put("provider", savedPayment.getProvider());
            response.put("createdAt", savedPayment.getCreatedAt());
            response.put("updatedAt", savedPayment.getUpdatedAt());

            logger.info("Started payment: {} for cart: {} amount: {}", 
                       savedPayment.getPaymentId(), request.getCartId(), savedPayment.getAmount());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to start payment for cart {}: {}", request.getCartId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable String paymentId) {
        logger.info("Getting payment status: {}", paymentId);

        try {
            Optional<Payment> paymentOpt = findPaymentByPaymentId(paymentId);
            
            if (paymentOpt.isEmpty()) {
                logger.warn("Payment not found: {}", paymentId);
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentOpt.get();
            String paymentState = getPaymentState(paymentId);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("paymentId", payment.getPaymentId());
            response.put("cartId", payment.getCartId());
            response.put("amount", payment.getAmount());
            response.put("status", paymentState != null ? paymentState : "INITIATED");
            response.put("provider", payment.getProvider());
            response.put("createdAt", payment.getCreatedAt());
            response.put("updatedAt", payment.getUpdatedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get payment status {}: {}", paymentId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
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

    // Request DTOs
    public static class StartPaymentRequest {
        private String cartId;

        public String getCartId() { return cartId; }
        public void setCartId(String cartId) { this.cartId = cartId; }
    }
}

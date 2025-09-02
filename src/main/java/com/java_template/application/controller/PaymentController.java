package com.java_template.application.controller;

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
@RequestMapping("/ui/payment")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final EntityService entityService;

    public PaymentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startPayment(@RequestBody Map<String, Object> request) {
        try {
            String cartId = (String) request.get("cartId");
            
            logger.info("Starting payment for cart: {}", cartId);

            // Get cart to validate and get amount
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                return ResponseEntity.badRequest().build();
            }

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId("pay_" + UUID.randomUUID().toString().substring(0, 8));
            payment.setCartId(cartId);
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(Instant.now());
            payment.setUpdatedAt(Instant.now());

            // Save payment - this will trigger start_dummy_payment transition
            EntityResponse<Payment> savedPayment = entityService.save(payment);

            Map<String, Object> response = convertToPaymentResponse(savedPayment.getData(), savedPayment.getMetadata().getState());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error starting payment", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<Map<String, Object>> getPayment(@PathVariable String paymentId) {
        try {
            logger.info("Getting payment: {}", paymentId);

            Payment payment = getPaymentByPaymentId(paymentId);
            if (payment == null) {
                return ResponseEntity.notFound().build();
            }

            // Get payment state
            String state = getPaymentState(paymentId);
            
            Map<String, Object> response = convertToPaymentResponse(payment, state);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting payment: {}", paymentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelPayment(@PathVariable String paymentId) {
        try {
            logger.info("Canceling payment: {}", paymentId);

            // Get payment
            Payment payment = getPaymentByPaymentId(paymentId);
            if (payment == null) {
                return ResponseEntity.notFound().build();
            }

            // Get payment entity ID for update
            UUID paymentEntityId = getPaymentEntityId(paymentId);
            if (paymentEntityId == null) {
                return ResponseEntity.notFound().build();
            }

            // Update payment with mark_canceled transition
            EntityResponse<Payment> updatedPayment = entityService.update(paymentEntityId, payment, "mark_canceled");

            Map<String, Object> response = new HashMap<>();
            response.put("paymentId", paymentId);
            response.put("status", updatedPayment.getMetadata().getState());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error canceling payment: {}", paymentId, e);
            return ResponseEntity.internalServerError().build();
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

    private UUID getPaymentEntityId(String paymentId) {
        // TODO: Implement proper entity ID lookup
        return null;
    }

    private String getPaymentState(String paymentId) {
        // TODO: Implement proper state lookup
        return "initiated";
    }

    private Map<String, Object> convertToPaymentResponse(Payment payment, String status) {
        Map<String, Object> response = new HashMap<>();
        response.put("paymentId", payment.getPaymentId());
        response.put("cartId", payment.getCartId());
        response.put("amount", payment.getAmount());
        response.put("status", status);
        response.put("provider", payment.getProvider());
        response.put("createdAt", payment.getCreatedAt());
        response.put("updatedAt", payment.getUpdatedAt());
        return response;
    }
}

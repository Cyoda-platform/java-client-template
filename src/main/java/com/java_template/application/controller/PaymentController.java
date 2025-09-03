package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
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
@RequestMapping("/ui/payment")
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final EntityService entityService;

    public PaymentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startPayment(@RequestBody Map<String, String> request) {
        try {
            String cartId = request.get("cartId");
            logger.info("Starting payment for cart: {}", cartId);

            if (cartId == null || cartId.trim().isEmpty()) {
                logger.warn("Cart ID is required for payment");
                return ResponseEntity.badRequest().build();
            }

            // Get cart to validate and get amount
            Cart cart = getCartByCartId(cartId);
            if (cart == null) {
                logger.warn("Cart not found: {}", cartId);
                return ResponseEntity.badRequest().build();
            }

            if (cart.getGrandTotal() == null || cart.getGrandTotal() <= 0) {
                logger.warn("Cart {} has invalid total: {}", cartId, cart.getGrandTotal());
                return ResponseEntity.badRequest().build();
            }

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId("pay-" + UUID.randomUUID().toString());
            payment.setCartId(cartId);
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(Instant.now());
            payment.setUpdatedAt(Instant.now());

            // CRITICAL: Pass payment entity directly - it IS the payload
            EntityResponse<Payment> response = entityService.save(payment);
            
            logger.info("Payment created: {} for cart: {}", payment.getPaymentId(), cartId);

            // Return payment ID for polling
            Map<String, String> responseMap = new HashMap<>();
            responseMap.put("paymentId", payment.getPaymentId());
            
            return ResponseEntity.ok(responseMap);

        } catch (Exception e) {
            logger.error("Error starting payment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable String paymentId) {
        try {
            logger.info("Getting payment status: {}", paymentId);

            // Search for payment by paymentId
            Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", paymentIdCondition);

            Optional<EntityResponse<Payment>> paymentResponse = entityService.getFirstItemByCondition(
                Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, condition, true);

            if (paymentResponse.isPresent()) {
                Payment payment = paymentResponse.get().getData();
                String status = paymentResponse.get().getMetadata().getState();
                
                // Build response
                Map<String, Object> response = new HashMap<>();
                response.put("paymentId", payment.getPaymentId());
                response.put("cartId", payment.getCartId());
                response.put("amount", payment.getAmount());
                response.put("status", status);
                response.put("provider", payment.getProvider());
                response.put("createdAt", payment.getCreatedAt());
                response.put("updatedAt", payment.getUpdatedAt());

                logger.info("Payment status: {} - {}", paymentId, status);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Payment not found: {}", paymentId);
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private Cart getCartByCartId(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", cartIdCondition);

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);

            return cartResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error getting cart by cartId: {}", cartId, e);
            return null;
        }
    }
}

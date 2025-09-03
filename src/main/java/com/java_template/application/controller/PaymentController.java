package com.java_template.application.controller;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
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
    public ResponseEntity<Object> startPayment(@RequestBody Map<String, Object> request) {
        try {
            String cartId = (String) request.get("cartId");

            logger.info("Starting payment for cart: {}", cartId);

            if (cartId == null || cartId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "INVALID_REQUEST", "message", "Cart ID is required"));
            }

            // Find cart to get amount
            var cartResponses = entityService.findByField(
                    Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, "cartId", cartId);

            if (cartResponses.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "CART_NOT_FOUND", "message", "Cart not found with ID: " + cartId));
            }

            Cart cart = cartResponses.get(0).getData();
            String cartState = cartResponses.get(0).getMetadata().getState();

            if (!"CHECKING_OUT".equals(cartState)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "INVALID_CART_STATE", "message", "Cart must be in CHECKING_OUT state"));
            }

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            payment.setCartId(cartId);
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            // Save payment with START_DUMMY_PAYMENT transition
            var savedPaymentResponse = entityService.save(payment);
            Payment savedPayment = savedPaymentResponse.getData();
            String paymentState = savedPaymentResponse.getMetadata().getState();

            // Update cart to CONVERTED state
            UUID cartEntityId = cartResponses.get(0).getMetadata().getId();
            entityService.update(cartEntityId, cart, "CHECKOUT");

            Map<String, Object> response = new HashMap<>();
            response.put("paymentId", savedPayment.getPaymentId());
            response.put("cartId", savedPayment.getCartId());
            response.put("amount", savedPayment.getAmount());
            response.put("status", paymentState);
            response.put("provider", savedPayment.getProvider());
            response.put("message", "Payment initiated, will auto-approve in ~3 seconds");

            logger.info("Payment started with ID: {}", savedPayment.getPaymentId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error starting payment: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "PAYMENT_ERROR", "message", "Failed to start payment: " + e.getMessage()));
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<Object> getPaymentStatus(@PathVariable String paymentId) {
        try {
            logger.info("Getting payment status: {}", paymentId);

            // Find payment
            var paymentResponses = entityService.findByField(
                    Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, "paymentId", paymentId);

            if (paymentResponses.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentResponses.get(0).getData();
            String paymentState = paymentResponses.get(0).getMetadata().getState();

            Map<String, Object> response = new HashMap<>();
            response.put("paymentId", payment.getPaymentId());
            response.put("cartId", payment.getCartId());
            response.put("amount", payment.getAmount());
            response.put("status", paymentState.toUpperCase());
            response.put("provider", payment.getProvider());
            response.put("createdAt", payment.getCreatedAt());
            response.put("updatedAt", payment.getUpdatedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting payment status {}: {}", paymentId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "PAYMENT_ERROR", "message", "Failed to get payment status: " + e.getMessage()));
        }
    }
}

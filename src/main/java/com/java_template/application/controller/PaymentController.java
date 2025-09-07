package com.java_template.application.controller;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment Controller - Manages dummy payment processing
 * 
 * Endpoints:
 * - POST /ui/payment/start - Start dummy payment process
 * - GET /ui/payment/{paymentId} - Get payment status (for polling)
 */
@RestController
@RequestMapping("/ui/payment")
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final EntityService entityService;

    public PaymentController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Start dummy payment process
     * POST /ui/payment/start
     */
    @PostMapping("/start")
    public ResponseEntity<PaymentStartResponse> startPayment(@RequestBody PaymentStartRequest request) {
        try {
            // Get cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                cartModelSpec, request.getCartId(), "cartId", Cart.class);
            
            if (cartResponse == null) {
                logger.error("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }
            
            Cart cart = cartResponse.entity();

            // Validate cart is in CHECKING_OUT state
            String currentState = cartResponse.getState();
            if (!"CHECKING_OUT".equals(currentState)) {
                logger.error("Cart {} is not in CHECKING_OUT state, current state: {}", request.getCartId(), currentState);
                return ResponseEntity.badRequest().build();
            }
            
            // Validate cart has items and guest contact
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                logger.error("Cannot start payment for empty cart: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }
            
            if (cart.getGuestContact() == null || cart.getGuestContact().getName() == null) {
                logger.error("Cannot start payment without guest contact: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }
            
            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());
            
            EntityWithMetadata<Payment> paymentResponse = entityService.create(payment);
            
            PaymentStartResponse response = new PaymentStartResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus("INITIATED");
            response.setMessage("Payment initiated, will auto-approve in ~3 seconds");
            
            logger.info("Payment started: {} for cart: {}", payment.getPaymentId(), request.getCartId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error starting payment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payment status (for polling)
     * GET /ui/payment/{paymentId}
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> response = entityService.findByBusinessId(
                paymentModelSpec, paymentId, "paymentId", Payment.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request and Response DTOs

    public static class PaymentStartRequest {
        private String cartId;

        public String getCartId() { return cartId; }
        public void setCartId(String cartId) { this.cartId = cartId; }
    }

    public static class PaymentStartResponse {
        private String paymentId;
        private String status;
        private String message;

        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}

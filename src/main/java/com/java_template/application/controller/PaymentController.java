package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PaymentController - Manages dummy payment processing.
 * 
 * Endpoints:
 * - POST /ui/payment/start - Start dummy payment process
 * - GET /ui/payment/{paymentId} - Get payment status for polling
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
    public ResponseEntity<EntityWithMetadata<Payment>> startPayment(@RequestBody StartPaymentRequest request) {
        try {
            // First get the cart to validate and get amount
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);
            
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Create payment entity
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            
            LocalDateTime now = LocalDateTime.now();
            payment.setCreatedAt(now);
            payment.setUpdatedAt(now);

            // Create payment with START_PAYMENT transition
            EntityWithMetadata<Payment> response = entityService.create(payment);
            
            logger.info("Payment started: {} for cart: {} with amount: {}", 
                       payment.getPaymentId(), request.getCartId(), payment.getAmount());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error starting payment for cart: {}", request.getCartId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payment status for polling
     * GET /ui/payment/{paymentId}
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);
            
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            String status = paymentWithMetadata.metadata().getState();

            PaymentStatusResponse response = new PaymentStatusResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setCartId(payment.getCartId());
            response.setAmount(payment.getAmount());
            response.setStatus(status.toUpperCase()); // Convert to uppercase for API consistency
            response.setProvider(payment.getProvider());
            response.setUpdatedAt(payment.getUpdatedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Request DTO for starting payment
     */
    @Data
    public static class StartPaymentRequest {
        private String cartId;
    }

    /**
     * Response DTO for payment status
     */
    @Data
    public static class PaymentStatusResponse {
        private String paymentId;
        private String cartId;
        private Double amount;
        private String status;
        private String provider;
        private LocalDateTime updatedAt;
    }
}

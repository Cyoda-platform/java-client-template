package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment Controller for OMS
 * Provides endpoints for dummy payment processing including start and status polling
 * Maps to /ui/payment/** endpoints
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
     * Body: { cartId }
     * Returns: { paymentId }
     */
    @PostMapping("/start")
    public ResponseEntity<PaymentStartResponse> startPayment(@RequestBody PaymentStartRequest request) {
        try {
            // Validate cart exists and get total amount
            EntityWithMetadata<Cart> cartWithMetadata = getCartByBusinessId(request.getCartId());
            if (cartWithMetadata == null) {
                logger.warn("Cart not found for payment: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();
            if (cart.getGrandTotal() == null || cart.getGrandTotal() <= 0) {
                logger.warn("Invalid cart total for payment: {}", cart.getGrandTotal());
                return ResponseEntity.badRequest().build();
            }

            // Create payment entity
            Payment payment = new Payment();
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            // Create payment with start_dummy_payment transition
            EntityWithMetadata<Payment> paymentResponse = entityService.create(payment);

            // Trigger auto payment processing (this will automatically transition to PAID after 3s)
            // The AutoMarkPaidAfter3s processor will handle the delay and state transition

            PaymentStartResponse response = new PaymentStartResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setAmount(payment.getAmount());
            response.setStatus("INITIATED");

            logger.info("Payment {} started for cart {} with amount {}", 
                       payment.getPaymentId(), request.getCartId(), payment.getAmount());

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
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                logger.warn("Payment not found for ID: {}", paymentId);
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            String currentState = paymentWithMetadata.metadata().getState();

            PaymentStatusResponse response = new PaymentStatusResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setCartId(payment.getCartId());
            response.setAmount(payment.getAmount());
            response.setStatus(currentState.toUpperCase()); // Map entity state to status
            response.setProvider(payment.getProvider());
            response.setCreatedAt(payment.getCreatedAt());
            response.setUpdatedAt(payment.getUpdatedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment status for ID: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel payment (manual operation)
     * POST /ui/payment/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<EntityWithMetadata<Payment>> cancelPayment(@PathVariable String paymentId) {
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
            payment.setUpdatedAt(LocalDateTime.now());

            // Cancel payment with manual transition
            EntityWithMetadata<Payment> response = entityService.update(
                    paymentWithMetadata.metadata().getId(), payment, "cancel_payment");

            logger.info("Payment {} cancelled", paymentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error cancelling payment: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods

    private EntityWithMetadata<Cart> getCartByBusinessId(String cartId) {
        ModelSpec modelSpec = new ModelSpec()
                .withName(Cart.ENTITY_NAME)
                .withVersion(Cart.ENTITY_VERSION);
        return entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
    }

    // Request/Response DTOs

    @Getter
    @Setter
    public static class PaymentStartRequest {
        private String cartId;
    }

    @Getter
    @Setter
    public static class PaymentStartResponse {
        private String paymentId;
        private Double amount;
        private String status;
    }

    @Getter
    @Setter
    public static class PaymentStatusResponse {
        private String paymentId;
        private String cartId;
        private Double amount;
        private String status; // "INITIATED" | "PAID" | "FAILED" | "CANCELED"
        private String provider;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}

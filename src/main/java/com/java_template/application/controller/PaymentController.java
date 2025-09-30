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
 * Payment controller for dummy payment processing in OMS
 * Handles payment initiation and status polling
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
     * Start dummy payment processing
     * POST /ui/payment/start
     */
    @PostMapping("/start")
    public ResponseEntity<PaymentStartResponse> startPayment(@RequestBody PaymentStartRequest request) {
        try {
            // Validate cart exists and is in CONVERTED state
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found for payment: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();
            if (!"CONVERTED".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CONVERTED state: {}", request.getCartId(), cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Create payment entity
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.create(payment);

            PaymentStartResponse startResponse = new PaymentStartResponse();
            startResponse.setPaymentId(payment.getPaymentId());
            startResponse.setStatus(payment.getStatus());
            startResponse.setAmount(payment.getAmount());

            logger.info("Payment {} started for cart {} with amount {}", 
                       payment.getPaymentId(), request.getCartId(), payment.getAmount());
            return ResponseEntity.ok(startResponse);
        } catch (Exception e) {
            logger.error("Error starting payment for cart: {}", request.getCartId(), e);
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
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();

            PaymentStatusResponse statusResponse = new PaymentStatusResponse();
            statusResponse.setPaymentId(payment.getPaymentId());
            statusResponse.setCartId(payment.getCartId());
            statusResponse.setStatus(payment.getStatus());
            statusResponse.setAmount(payment.getAmount());
            statusResponse.setProvider(payment.getProvider());
            statusResponse.setCreatedAt(payment.getCreatedAt());
            statusResponse.setUpdatedAt(payment.getUpdatedAt());

            return ResponseEntity.ok(statusResponse);
        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel payment (manual operation)
     * POST /ui/payment/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentStatusResponse> cancelPayment(@PathVariable String paymentId) {
        try {
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();

            // Only allow cancellation if payment is still INITIATED
            if (!"INITIATED".equals(payment.getStatus())) {
                logger.warn("Cannot cancel payment {} in status: {}", paymentId, payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            EntityWithMetadata<Payment> response = entityService.update(
                    paymentWithMetadata.metadata().getId(), payment, "cancel_payment");

            PaymentStatusResponse statusResponse = new PaymentStatusResponse();
            statusResponse.setPaymentId(response.entity().getPaymentId());
            statusResponse.setCartId(response.entity().getCartId());
            statusResponse.setStatus(response.entity().getStatus());
            statusResponse.setAmount(response.entity().getAmount());
            statusResponse.setProvider(response.entity().getProvider());
            statusResponse.setCreatedAt(response.entity().getCreatedAt());
            statusResponse.setUpdatedAt(response.entity().getUpdatedAt());

            logger.info("Payment {} cancelled", paymentId);
            return ResponseEntity.ok(statusResponse);
        } catch (Exception e) {
            logger.error("Error cancelling payment: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request/Response DTOs

    @Data
    public static class PaymentStartRequest {
        private String cartId;
    }

    @Data
    public static class PaymentStartResponse {
        private String paymentId;
        private String status;
        private Double amount;
    }

    @Data
    public static class PaymentStatusResponse {
        private String paymentId;
        private String cartId;
        private String status;
        private Double amount;
        private String provider;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}

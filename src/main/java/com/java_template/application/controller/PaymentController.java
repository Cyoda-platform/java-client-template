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
 * ABOUTME: Payment controller providing REST endpoints for dummy payment processing
 * with automatic approval after 3 seconds for demo purposes.
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
     * Start dummy payment
     * POST /ui/payment/start
     */
    @PostMapping("/start")
    public ResponseEntity<PaymentResponse> startPayment(@RequestBody StartPaymentRequest request) {
        try {
            // Validate cart exists and is in CONVERTED status
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();
            if (!"CONVERTED".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CONVERTED status: {}", request.getCartId(), cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> paymentResponse = entityService.create(payment);

            // Trigger auto-payment workflow (this will automatically transition to PAID after 3s)
            Payment updatedPayment = paymentResponse.entity();
            EntityWithMetadata<Payment> finalResponse = entityService.update(
                    paymentResponse.metadata().getId(), updatedPayment, "auto_mark_paid");

            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(payment.getStatus());
            response.setAmount(payment.getAmount());

            logger.info("Payment started: {} for cart: {}", payment.getPaymentId(), request.getCartId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error starting payment for cart: {}", request.getCartId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payment status
     * GET /ui/payment/{paymentId}
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec paymentModelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();

            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(payment.getStatus());
            response.setAmount(payment.getAmount());
            response.setCartId(payment.getCartId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Check if payment is completed (PAID status)
     * GET /ui/payment/{paymentId}/status
     */
    @GetMapping("/{paymentId}/status")
    public ResponseEntity<PaymentStatusResponse> checkPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec paymentModelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();

            PaymentStatusResponse response = new PaymentStatusResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(payment.getStatus());
            response.setCompleted("PAID".equals(payment.getStatus()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error checking payment status: {}", paymentId, e);
            return ResponseEntity.notFound().build();
        }
    }

    // Request/Response DTOs

    @Getter
    @Setter
    public static class StartPaymentRequest {
        private String cartId;
    }

    @Getter
    @Setter
    public static class PaymentResponse {
        private String paymentId;
        private String status;
        private Double amount;
        private String cartId;
    }

    @Getter
    @Setter
    public static class PaymentStatusResponse {
        private String paymentId;
        private String status;
        private Boolean completed;
    }
}

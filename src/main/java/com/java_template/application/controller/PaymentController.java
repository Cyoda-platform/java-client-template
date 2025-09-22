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
 * Payment Controller - Dummy payment processing
 * 
 * Provides endpoints for:
 * - Start dummy payment
 * - Poll payment status
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
    public ResponseEntity<PaymentStartResponse> startPayment(@RequestBody PaymentStartRequest request) {
        try {
            // Get cart to validate and get amount
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart is in CONVERTED status (after checkout)
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

            // Create payment entity
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.create(payment);

            // Start dummy payment processing
            entityService.update(paymentWithMetadata.metadata().getId(), payment, "start_dummy_payment");

            PaymentStartResponse response = new PaymentStartResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(payment.getStatus());
            response.setAmount(payment.getAmount());

            logger.info("Payment {} started for cart {} with amount ${}", 
                       payment.getPaymentId(), request.getCartId(), payment.getAmount());

            return ResponseEntity.ok(response);
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
            response.setCartId(payment.getCartId());
            response.setStatus(payment.getStatus());
            response.setAmount(payment.getAmount());
            response.setProvider(payment.getProvider());
            response.setCreatedAt(payment.getCreatedAt());
            response.setUpdatedAt(payment.getUpdatedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment status for payment: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
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
        private String status;
        private Double amount;
    }

    @Getter
    @Setter
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

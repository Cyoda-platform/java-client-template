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
 * Payment Controller - REST API for payment processing
 * 
 * Provides endpoints for:
 * - Starting dummy payments
 * - Polling payment status
 * - Managing payment lifecycle
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
            // Validate cart exists and is in CHECKING_OUT status
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
            
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.warn("Cart {} not in CHECKING_OUT status: {}", request.getCartId(), cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Generate payment ID
            String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            
            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId(paymentId);
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.create(payment);
            
            logger.info("Payment started: {} for cart: {} amount: {}", 
                    paymentId, request.getCartId(), cart.getGrandTotal());

            PaymentResponse paymentResponse = new PaymentResponse();
            paymentResponse.setPaymentId(paymentId);
            paymentResponse.setStatus(payment.getStatus());
            paymentResponse.setAmount(payment.getAmount());
            
            return ResponseEntity.ok(paymentResponse);
        } catch (Exception e) {
            logger.error("Error starting payment", e);
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
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);
            
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            
            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(payment.getStatus());
            response.setAmount(payment.getAmount());
            response.setCartId(payment.getCartId());
            response.setProvider(payment.getProvider());
            response.setCreatedAt(payment.getCreatedAt());
            response.setPaidAt(payment.getPaidAt());
            response.setFailureReason(payment.getFailureReason());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel payment
     * POST /ui/payment/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(@PathVariable String paymentId) {
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
            
            if (!"INITIATED".equals(payment.getStatus())) {
                logger.warn("Cannot cancel payment {} in status: {}", paymentId, payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            payment.setStatus("CANCELED");
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.update(
                    paymentWithMetadata.metadata().getId(), payment, "cancel_payment");
            
            logger.info("Payment canceled: {}", paymentId);

            PaymentResponse paymentResponse = new PaymentResponse();
            paymentResponse.setPaymentId(payment.getPaymentId());
            paymentResponse.setStatus(payment.getStatus());
            paymentResponse.setAmount(payment.getAmount());
            
            return ResponseEntity.ok(paymentResponse);
        } catch (Exception e) {
            logger.error("Error canceling payment", e);
            return ResponseEntity.badRequest().build();
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
        private String provider;
        private LocalDateTime createdAt;
        private LocalDateTime paidAt;
        private String failureReason;
    }
}

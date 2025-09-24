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
 * PaymentController - REST endpoints for payment processing
 * 
 * Provides endpoints for:
 * - Payment initiation (dummy payment)
 * - Payment status polling
 * - Payment retrieval
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
     * Start dummy payment for cart
     * POST /ui/payment/start
     */
    @PostMapping("/start")
    public ResponseEntity<PaymentStartResponse> startPayment(@RequestBody PaymentStartRequest request) {
        try {
            // Validate cart exists and is in CHECKING_OUT status
            Cart cart = getCartById(request.getCartId());
            if (cart == null) {
                logger.warn("Cart not found for payment: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CHECKING_OUT status: {}", request.getCartId(), cart.getStatus());
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

            // Create payment entity (will trigger auto-approval after 3s)
            EntityWithMetadata<Payment> paymentResponse = entityService.create(payment);
            
            // Trigger auto-approval workflow
            entityService.update(paymentResponse.metadata().getId(), payment, "auto_mark_paid");

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
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> response = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payment by technical UUID
     * GET /ui/payment/id/{id}
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> response = entityService.getById(id, modelSpec, Payment.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Cancel payment
     * POST /ui/payment/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<EntityWithMetadata<Payment>> cancelPayment(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentResponse = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentResponse.entity();

            // Only allow cancellation if payment is still INITIATED
            if (!"INITIATED".equals(payment.getStatus())) {
                logger.warn("Cannot cancel payment {} with status: {}", paymentId, payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Cancel payment
            EntityWithMetadata<Payment> response = entityService.update(
                    paymentResponse.metadata().getId(), payment, "cancel_payment");
            
            logger.info("Payment {} cancelled", paymentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error cancelling payment: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get cart by ID
     */
    private Cart getCartById(String cartId) {
        try {
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);
            return cartResponse != null ? cartResponse.entity() : null;
        } catch (Exception e) {
            logger.error("Error getting cart by ID: {}", cartId, e);
            return null;
        }
    }

    /**
     * Request DTO for starting payment
     */
    @Getter
    @Setter
    public static class PaymentStartRequest {
        private String cartId;
    }

    /**
     * Response DTO for payment start
     */
    @Getter
    @Setter
    public static class PaymentStartResponse {
        private String paymentId;
        private String status;
        private Double amount;
    }
}

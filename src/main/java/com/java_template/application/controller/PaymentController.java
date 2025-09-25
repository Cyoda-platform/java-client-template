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
 * Payment Controller for OMS system
 * Handles dummy payment processing with auto-approval after 3 seconds
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
            // Validate cart exists and is in CHECKING_OUT status
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.error("Cart not found: {}", request.getCartId());
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.error("Cart {} is not in CHECKING_OUT status: {}", request.getCartId(), cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> paymentResponse = entityService.create(payment);
            
            // Trigger auto-payment processor (will mark as PAID after 3 seconds)
            entityService.update(paymentResponse.metadata().getId(), payment, "auto_mark_paid");
            
            PaymentStartResponse response = new PaymentStartResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(payment.getStatus());
            response.setAmount(payment.getAmount());
            
            logger.info("Payment {} started for cart {} with amount ${}", 
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
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> payment = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (payment == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(payment);
        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payment by technical ID
     * GET /ui/payment/id/{id}
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> payment = entityService.getById(id, modelSpec, Payment.class);
            return ResponseEntity.ok(payment);
        } catch (Exception e) {
            logger.error("Error getting payment by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Cancel payment (manual operation)
     * POST /ui/payment/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<EntityWithMetadata<Payment>> cancelPayment(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            
            // Only allow cancellation if payment is still INITIATED
            if (!"INITIATED".equals(payment.getStatus())) {
                logger.error("Cannot cancel payment {} in status: {}", paymentId, payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            EntityWithMetadata<Payment> response = entityService.update(
                    paymentWithMetadata.metadata().getId(), payment, "cancel_payment");
            
            logger.info("Payment {} cancelled", paymentId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error cancelling payment: {}", paymentId, e);
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
}

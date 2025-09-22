package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Payment Controller - Handles payment operations for OMS
 * 
 * Provides endpoints for:
 * - Payment initiation (dummy payment)
 * - Payment status polling
 */
@RestController
@RequestMapping("/ui/payment")
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PaymentController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Start payment process
     * POST /ui/payment/start
     */
    @PostMapping("/start")
    public ResponseEntity<PaymentStartResponse> startPayment(@RequestBody PaymentStartRequest request) {
        try {
            logger.info("Starting payment for cart: {}", request.getCartId());

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
                logger.warn("Cart not in CHECKING_OUT status: {} - Status: {}", request.getCartId(), cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate cart has guest contact information
            if (cart.getGuestContact() == null || 
                cart.getGuestContact().getName() == null || 
                cart.getGuestContact().getAddress() == null) {
                logger.warn("Cart missing guest contact information: {}", request.getCartId());
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

            // Create payment in Cyoda
            EntityWithMetadata<Payment> createdPayment = entityService.create(payment);

            // Start dummy payment processing with transition
            EntityWithMetadata<Payment> processedPayment = entityService.update(
                    createdPayment.metadata().getId(), 
                    createdPayment.entity(), 
                    "start_dummy_payment");

            PaymentStartResponse response = new PaymentStartResponse();
            response.setPaymentId(processedPayment.entity().getPaymentId());
            response.setStatus(processedPayment.entity().getStatus());
            response.setAmount(processedPayment.entity().getAmount());

            logger.info("Payment started: {} for cart: {}", response.getPaymentId(), request.getCartId());
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
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentStatus(@PathVariable String paymentId) {
        try {
            logger.info("Getting payment status: {}", paymentId);

            ModelSpec modelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> payment = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (payment == null) {
                logger.warn("Payment not found: {}", paymentId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(payment);
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
    public ResponseEntity<EntityWithMetadata<Payment>> cancelPayment(@PathVariable String paymentId) {
        try {
            logger.info("Canceling payment: {}", paymentId);

            ModelSpec modelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                logger.warn("Payment not found: {}", paymentId);
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();

            // Only allow cancellation if payment is not already PAID
            if ("PAID".equals(payment.getStatus())) {
                logger.warn("Cannot cancel already paid payment: {}", paymentId);
                return ResponseEntity.badRequest().build();
            }

            // Cancel payment with transition
            EntityWithMetadata<Payment> canceledPayment = entityService.update(
                    paymentWithMetadata.metadata().getId(), 
                    payment, 
                    "mark_canceled");

            logger.info("Payment canceled: {}", paymentId);
            return ResponseEntity.ok(canceledPayment);
        } catch (Exception e) {
            logger.error("Error canceling payment: {}", paymentId, e);
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

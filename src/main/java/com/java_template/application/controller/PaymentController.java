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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ABOUTME: This file contains the PaymentController that exposes REST APIs for dummy payment
 * processing with automatic approval after 3 seconds for demo purposes.
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
    public ResponseEntity<?> startPayment(@RequestBody StartPaymentRequest request) {
        try {
            // Validate cart exists and is in CHECKING_OUT status
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessIdOrNull(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartResponse == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Cart not found: " + request.getCartId());
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartResponse.entity();
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, 
                    "Cart must be in CHECKING_OUT status. Current status: " + cart.getStatus());
                return ResponseEntity.of(problemDetail).build();
            }

            // Validate cart has items and total
            if (cart.getGrandTotal() == null || cart.getGrandTotal() <= 0) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Cart has no items or invalid total");
                return ResponseEntity.of(problemDetail).build();
            }

            // Generate unique payment ID
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

            // Create payment entity - this will trigger CreateDummyPayment processor
            EntityWithMetadata<Payment> paymentResponse = entityService.create(payment);
            
            logger.info("Payment {} started for cart {} with amount {}", 
                    paymentId, request.getCartId(), cart.getGrandTotal());

            // Return payment ID for polling
            PaymentStartResponse response = new PaymentStartResponse();
            response.setPaymentId(paymentId);
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
            EntityWithMetadata<Payment> response = entityService.findByBusinessIdOrNull(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment status for ID: {}", paymentId, e);
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
     * Cancel payment (manual operation)
     * POST /ui/payment/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<?> cancelPayment(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentResponse = entityService.findByBusinessIdOrNull(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentResponse.entity();
            
            // Can only cancel INITIATED payments
            if (!"INITIATED".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, 
                    "Can only cancel INITIATED payments. Current status: " + payment.getStatus());
                return ResponseEntity.of(problemDetail).build();
            }

            // Update payment status to CANCELED
            payment.setStatus("CANCELED");
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> updatedPayment = entityService.update(
                    paymentResponse.metadata().getId(), payment, "cancel_payment");
            
            logger.info("Payment {} canceled", paymentId);
            return ResponseEntity.ok(updatedPayment);
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
    public static class PaymentStartResponse {
        private String paymentId;
    }
}

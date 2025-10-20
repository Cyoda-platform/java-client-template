package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Payment operations including dummy payment processing,
 * payment status polling, and payment lifecycle management.
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
    public ResponseEntity<PaymentResponse> startPayment(@Valid @RequestBody StartPaymentRequest request) {
        try {
            // Validate cart exists and is in CONVERTED status
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Cart not found with ID: %s", request.getCartId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartWithMetadata.entity();
            if (!"CONVERTED".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Cart must be CONVERTED to start payment, current status: %s", cart.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Generate payment ID
            String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // Create payment entity
            Payment payment = new Payment();
            payment.setPaymentId(paymentId);
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setStatus("NEW"); // Will be set to INITIATED by processor
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.create(payment);
            logger.info("Payment started with ID: {}, amount: {}", paymentId, payment.getAmount());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{paymentId}")
                .buildAndExpand(paymentId)
                .toUri();

            PaymentResponse paymentResponse = new PaymentResponse();
            paymentResponse.setPaymentId(paymentId);
            paymentResponse.setStatus(response.entity().getStatus());
            paymentResponse.setAmount(response.entity().getAmount());

            return ResponseEntity.created(location).body(paymentResponse);
        } catch (Exception e) {
            logger.error("Failed to start payment", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to start payment: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get payment status
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
            logger.error("Failed to retrieve payment with ID: {}", paymentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve payment with ID '%s': %s", paymentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            
            if (!"INITIATED".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Payment can only be canceled when INITIATED, current status: %s", payment.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Payment> response = entityService.update(
                    paymentWithMetadata.metadata().getId(), payment, "cancel_payment");
            logger.info("Payment canceled: {}", paymentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to cancel payment: {}", paymentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to cancel payment '%s': %s", paymentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Mark payment as failed (for testing)
     * POST /ui/payment/{paymentId}/fail
     */
    @PostMapping("/{paymentId}/fail")
    public ResponseEntity<EntityWithMetadata<Payment>> failPayment(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            
            if (!"INITIATED".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Payment can only be failed when INITIATED, current status: %s", payment.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Payment> response = entityService.update(
                    paymentWithMetadata.metadata().getId(), payment, "mark_failed");
            logger.info("Payment marked as failed: {}", paymentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to mark payment as failed: {}", paymentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to mark payment as failed '%s': %s", paymentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    // Request and Response DTOs
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
    }
}

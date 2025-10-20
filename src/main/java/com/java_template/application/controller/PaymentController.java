package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.Data;
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
    public ResponseEntity<PaymentResponse> startPayment(@Valid @RequestBody StartPaymentRequest request) {
        try {
            // Validate cart exists and is in CHECKING_OUT status
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

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
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Cart must be in CHECKING_OUT status. Current status: %s", cart.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8));
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> createdPayment = entityService.create(payment);

            // Trigger auto-approval processor (will happen automatically via workflow)
            EntityWithMetadata<Payment> updatedPayment = entityService.update(
                    createdPayment.metadata().getId(), 
                    createdPayment.entity(), 
                    "auto_mark_paid");

            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(createdPayment.entity().getPaymentId());
            response.setStatus(updatedPayment.entity().getStatus());
            response.setAmount(updatedPayment.entity().getAmount());

            logger.info("Payment started for cart {}: {}", request.getCartId(), response.getPaymentId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{paymentId}")
                .buildAndExpand(response.getPaymentId())
                .toUri();

            return ResponseEntity.created(location).body(response);

        } catch (Exception e) {
            logger.error("Failed to start payment for cart: {}", request.getCartId(), e);
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
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> payment = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (payment == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(payment);

        } catch (Exception e) {
            logger.error("Failed to get payment status: {}", paymentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to get payment status for '%s': %s", paymentId, e.getMessage())
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
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Payment can only be canceled when INITIATED. Current status: %s", payment.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Cancel payment
            EntityWithMetadata<Payment> response = entityService.update(
                    paymentWithMetadata.metadata().getId(), payment, "cancel_payment");

            logger.info("Payment canceled: {}", paymentId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to cancel payment: {}", paymentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to cancel payment: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @Data
    public static class StartPaymentRequest {
        private String cartId;
    }

    @Data
    public static class PaymentResponse {
        private String paymentId;
        private String status;
        private Double amount;
    }
}

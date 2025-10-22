package com.java_template.application.controller;

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
 * ABOUTME: REST controller for Payment operations including dummy payment creation,
 * status polling, and automatic payment processing simulation.
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
    public ResponseEntity<PaymentResponse> startPayment(@Valid @RequestBody StartPaymentRequest request) {
        try {
            // Create new payment
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(request.getAmount());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.create(payment);
            logger.info("Payment started with ID: {} for cart: {}", payment.getPaymentId(), payment.getCartId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/../{paymentId}")
                .buildAndExpand(payment.getPaymentId())
                .toUri();

            PaymentResponse paymentResponse = new PaymentResponse();
            paymentResponse.setPaymentId(payment.getPaymentId());
            paymentResponse.setStatus(payment.getStatus());
            paymentResponse.setAmount(payment.getAmount());

            return ResponseEntity.created(location).body(paymentResponse);
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
     * Get payment status by payment ID
     * GET /ui/payment/{paymentId}
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
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

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve payment status for ID: {}", paymentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve payment status for ID '%s': %s", paymentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Cancel payment
     * POST /ui/payment/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            
            // Only allow cancellation if payment is still in INITIATED status
            if (!"INITIATED".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Cannot cancel payment in status: %s", payment.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            payment.setStatus("CANCELED");
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> updatedPayment = entityService.update(
                    paymentWithMetadata.metadata().getId(), payment, "cancel_payment");
            
            logger.info("Payment canceled: {}", paymentId);

            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(payment.getStatus());
            response.setAmount(payment.getAmount());
            response.setCartId(payment.getCartId());

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
     * Request DTO for starting payment
     */
    @Getter
    @Setter
    public static class StartPaymentRequest {
        private String cartId;
        private Double amount;
    }

    /**
     * Response DTO for payment operations
     */
    @Getter
    @Setter
    public static class PaymentResponse {
        private String paymentId;
        private String status;
        private Double amount;
        private String cartId;
    }
}

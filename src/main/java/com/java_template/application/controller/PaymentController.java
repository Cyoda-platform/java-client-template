package com.java_template.application.controller;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
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
 * ABOUTME: Payment controller providing dummy payment processing with
 * automatic approval after 3 seconds for demo purposes.
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
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(request.getAmount());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.create(payment);
            
            PaymentResponse paymentResponse = new PaymentResponse();
            paymentResponse.setPaymentId(payment.getPaymentId());
            paymentResponse.setStatus(payment.getStatus());
            
            logger.info("Started dummy payment {} for cart {} amount ${}", 
                       payment.getPaymentId(), request.getCartId(), request.getAmount());
            return ResponseEntity.ok(paymentResponse);
        } catch (Exception e) {
            logger.error("Failed to start payment: {}", e.getMessage(), e);
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
            logger.error("Failed to get payment status {}: {}", paymentId, e.getMessage(), e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve payment status: %s", e.getMessage())
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
            
            // Only allow cancellation if payment is still initiated
            if (!"INITIATED".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Payment can only be cancelled when in INITIATED status"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            payment.setStatus("CANCELED");
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.update(
                    paymentWithMetadata.metadata().getId(), payment, "cancel_payment");
            
            logger.info("Cancelled payment {}", paymentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to cancel payment {}: {}", paymentId, e.getMessage(), e);
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
        private Double amount;
    }

    @Data
    public static class PaymentResponse {
        private String paymentId;
        private String status;
    }
}

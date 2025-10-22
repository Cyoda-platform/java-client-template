package com.java_template.application.controller;

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
 * ABOUTME: PaymentController exposes REST APIs for payment operations
 * including starting dummy payments and polling payment status.
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
     * Start a dummy payment
     * POST /ui/payment/start
     * Body: { "cartId": "...", "amount": ... }
     * Returns: { "paymentId": "..." }
     */
    @PostMapping("/start")
    public ResponseEntity<PaymentStartResponse> startPayment(@RequestBody PaymentStartRequest request) {
        try {
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(request.getAmount());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.create(payment);
            
            // Trigger auto-mark-paid transition
            entityService.update(response.metadata().getId(), response.entity(), "auto_mark_paid");

            PaymentStartResponse startResponse = new PaymentStartResponse();
            startResponse.setPaymentId(response.entity().getPaymentId());

            logger.info("Payment started with ID: {}", response.entity().getPaymentId());
            return ResponseEntity.ok(startResponse);
        } catch (Exception e) {
            logger.error("Error starting payment", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Failed to start payment: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get payment status by ID
     * GET /ui/payment/{paymentId}
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPayment(@PathVariable String paymentId) {
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
            logger.error("Error retrieving payment: {}", paymentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Failed to retrieve payment: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @Getter
    @Setter
    public static class PaymentStartRequest {
        private String cartId;
        private Double amount;
    }

    @Getter
    @Setter
    public static class PaymentStartResponse {
        private String paymentId;
    }
}


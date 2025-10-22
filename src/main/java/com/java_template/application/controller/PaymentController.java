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
 * ABOUTME: Payment REST controller exposing payment endpoints for starting
 * dummy payments and polling payment status.
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
     * Body: { "cartId": "..." }
     */
    @PostMapping("/start")
    public ResponseEntity<PaymentStartResponse> startPayment(@Valid @RequestBody StartPaymentRequest request) {
        try {
            Payment payment = new Payment();
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            payment.setCartId(request.getCartId());
            payment.setAmount(request.getAmount());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.create(payment);
            logger.info("Payment started with ID: {}", payment.getPaymentId());

            PaymentStartResponse startResponse = new PaymentStartResponse();
            startResponse.setPaymentId(payment.getPaymentId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{paymentId}")
                .buildAndExpand(payment.getPaymentId())
                .toUri();

            return ResponseEntity.created(location).body(startResponse);
        } catch (Exception e) {
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
    public ResponseEntity<EntityWithMetadata<Payment>> getPayment(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> response = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve payment with ID '%s': %s", paymentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Request DTOs
     */
    @Getter
    @Setter
    public static class StartPaymentRequest {
        private String cartId;
        private Double amount;
    }

    @Getter
    @Setter
    public static class PaymentStartResponse {
        private String paymentId;
    }
}


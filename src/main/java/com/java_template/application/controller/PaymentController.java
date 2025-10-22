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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Payment endpoints including dummy payment creation and status polling.
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
    public ResponseEntity<PaymentStartResponse> startPayment(@RequestBody PaymentStartRequest request) {
        try {
            // Verify cart exists and get its total
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.create(payment);
            logger.info("Dummy payment created with ID: {}", response.metadata().getId());

            PaymentStartResponse paymentResponse = new PaymentStartResponse();
            paymentResponse.setPaymentId(payment.getPaymentId());

            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(payment.getPaymentId())
                    .toUri();

            return ResponseEntity.created(location).body(paymentResponse);
        } catch (Exception e) {
            logger.error("Failed to start payment: {}", e.getMessage());
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
            logger.error("Failed to get payment {}: {}", paymentId, e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve payment: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @Getter
    @Setter
    public static class PaymentStartRequest {
        private String cartId;
    }

    @Getter
    @Setter
    public static class PaymentStartResponse {
        private String paymentId;
    }
}


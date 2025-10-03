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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ABOUTME: Payment controller providing REST APIs for dummy payment processing
 * with automatic approval after 3 seconds for demo purposes.
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
     * Start dummy payment
     * POST /ui/payment/start
     */
    @PostMapping("/start")
    public ResponseEntity<?> startPayment(@RequestBody StartPaymentRequest request) {
        try {
            // Validate cart exists and is in CHECKING_OUT status
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Cart not found: " + request.getCartId());
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartWithMetadata.entity();

            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, 
                    "Cart must be in CHECKING_OUT status. Current status: " + cart.getStatus());
                return ResponseEntity.of(problemDetail).build();
            }

            // Check if payment already exists for this cart
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            
            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.create(payment);
            logger.info("Payment {} started for cart {}", payment.getPaymentId(), request.getCartId());
            
            // Return payment ID for polling
            PaymentResponse paymentResponse = new PaymentResponse();
            paymentResponse.setPaymentId(payment.getPaymentId());
            paymentResponse.setStatus(payment.getStatus());
            paymentResponse.setAmount(payment.getAmount());
            
            return ResponseEntity.ok(paymentResponse);
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
    public ResponseEntity<?> getPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessIdOrNull(
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
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
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
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessIdOrNull(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();

            if (!"INITIATED".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, 
                    "Can only cancel payments in INITIATED status. Current status: " + payment.getStatus());
                return ResponseEntity.of(problemDetail).build();
            }

            // Update payment status to CANCELED
            EntityWithMetadata<Payment> response = entityService.update(
                    paymentWithMetadata.metadata().getId(), payment, "cancel_payment");
            
            logger.info("Payment {} canceled", paymentId);
            
            PaymentResponse paymentResponse = new PaymentResponse();
            paymentResponse.setPaymentId(response.entity().getPaymentId());
            paymentResponse.setStatus(response.entity().getStatus());
            paymentResponse.setAmount(response.entity().getAmount());
            paymentResponse.setCartId(response.entity().getCartId());
            
            return ResponseEntity.ok(paymentResponse);
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
    public static class PaymentResponse {
        private String paymentId;
        private String status;
        private Double amount;
        private String cartId;
    }
}

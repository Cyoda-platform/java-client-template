package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment Controller for OMS dummy payment processing
 * Provides REST endpoints for payment initiation and status checking
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
    public ResponseEntity<PaymentResponse> startPayment(@RequestBody StartPaymentRequest request) {
        logger.info("Starting payment for cart: {}", request.getCartId());

        try {
            // Validate cart exists and is in correct state
            ModelSpec cartModelSpec = new ModelSpec();
            cartModelSpec.setName(Cart.ENTITY_NAME);
            cartModelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(cartModelSpec, request.getCartId(), "cartId", Cart.class);
            
            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            if (!"CONVERTED".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CONVERTED state: {}", request.getCartId(), cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Generate unique payment ID
            String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            
            // Create payment entity
            Payment payment = new Payment();
            payment.setPaymentId(paymentId);
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            // Save payment - this will trigger the workflow
            EntityWithMetadata<Payment> savedPayment = entityService.create(payment);
            UUID technicalId = savedPayment.metadata().getId();

            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(paymentId);
            response.setStatus("INITIATED");
            response.setAmount(cart.getGrandTotal());

            logger.info("Started payment: {} for cart: {} (technical: {})", paymentId, request.getCartId(), technicalId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error starting payment for cart: {}", request.getCartId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get payment status
     * GET /ui/payment/{paymentId}
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable String paymentId) {
        logger.info("Getting payment status: {}", paymentId);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Payment.ENTITY_NAME);
            modelSpec.setVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(modelSpec, paymentId, "paymentId", Payment.class);
            
            if (paymentWithMetadata == null) {
                logger.warn("Payment not found: {}", paymentId);
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            
            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(payment.getStatus());
            response.setAmount(payment.getAmount());
            response.setCartId(payment.getCartId());
            response.setCreatedAt(payment.getCreatedAt());
            response.setPaidAt(payment.getPaidAt());

            logger.info("Payment {} status: {}", paymentId, payment.getStatus());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Request DTO for starting payment
     */
    @Data
    public static class StartPaymentRequest {
        private String cartId;
    }

    /**
     * Response DTO for payment operations
     */
    @Data
    public static class PaymentResponse {
        private String paymentId;
        private String status;
        private Double amount;
        private String cartId;
        private LocalDateTime createdAt;
        private LocalDateTime paidAt;
    }
}

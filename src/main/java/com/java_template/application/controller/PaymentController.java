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
 * PaymentController - REST endpoints for payment operations
 * 
 * Provides UI-facing APIs for dummy payment processing with automatic approval.
 * Base path: /ui/payment
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
     * POST /ui/payment/start - Start dummy payment
     */
    @PostMapping("/start")
    public ResponseEntity<PaymentResponse> startPayment(@RequestBody StartPaymentRequest request) {
        try {
            // Find cart to get amount
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);
            
            if (cartWithMetadata == null) {
                logger.error("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Create payment entity
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            
            LocalDateTime now = LocalDateTime.now();
            payment.setCreatedAt(now);
            payment.setUpdatedAt(now);

            // Create payment with START_DUMMY_PAYMENT transition
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.create(payment);
            
            // Convert cart to CONVERTED state
            entityService.update(cartWithMetadata.metadata().getId(), cart, "CHECKOUT");
            
            logger.info("Payment {} started for cart {} with amount {}", 
                       payment.getPaymentId(), request.getCartId(), payment.getAmount());

            // Create response
            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(paymentWithMetadata.metadata().getState());
            response.setAmount(payment.getAmount());
            response.setProvider(payment.getProvider());
            response.setCreatedAt(payment.getCreatedAt());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error starting payment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /ui/payment/{paymentId} - Poll payment status
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, paymentId, "paymentId", Payment.class);
            
            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            
            // Create response
            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(paymentWithMetadata.metadata().getState());
            response.setAmount(payment.getAmount());
            response.setProvider(payment.getProvider());
            response.setCreatedAt(payment.getCreatedAt());
            response.setUpdatedAt(payment.getUpdatedAt());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request/Response DTOs

    @Data
    public static class StartPaymentRequest {
        private String cartId;
    }

    @Data
    public static class PaymentResponse {
        private String paymentId;
        private String status;
        private java.math.BigDecimal amount;
        private String provider;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}

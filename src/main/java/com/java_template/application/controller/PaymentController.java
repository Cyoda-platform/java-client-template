package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PaymentController - UI-facing REST controller for payment operations
 * 
 * This controller provides:
 * - Dummy payment creation and processing
 * - Payment status polling
 * - Auto-approval simulation (3 seconds)
 * 
 * Endpoints:
 * - POST /ui/payment/start - Start dummy payment
 * - GET /ui/payment/{paymentId} - Get payment status
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
            // Get cart to validate and get amount
            EntityWithMetadata<Cart> cartWithMetadata = getCartByCartId(request.getCartId());
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Validate cart is in CONVERTED state
            if (!"CONVERTED".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CONVERTED state: {}", request.getCartId(), cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8));
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            
            LocalDateTime now = LocalDateTime.now();
            payment.setCreatedAt(now);
            payment.setUpdatedAt(now);

            EntityWithMetadata<Payment> response = entityService.create(payment);
            
            // Simulate auto-approval after 3 seconds (in real implementation, this would be handled by processors)
            // For demo purposes, we'll immediately trigger the auto-approval process
            scheduleAutoApproval(response.metadata().getId());
            
            PaymentStartResponse startResponse = new PaymentStartResponse();
            startResponse.setPaymentId(response.entity().getPaymentId());
            
            logger.info("Payment {} started for cart {} with amount {}", 
                       payment.getPaymentId(), request.getCartId(), payment.getAmount());
            return ResponseEntity.ok(startResponse);
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
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(Payment.ENTITY_NAME)
                .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> response = entityService.findByBusinessId(
                modelSpec, paymentId, "paymentId", Payment.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment status for ID: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods

    private EntityWithMetadata<Cart> getCartByCartId(String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(Cart.ENTITY_NAME)
                .withVersion(Cart.ENTITY_VERSION);

            return entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
        } catch (Exception e) {
            logger.error("Error finding cart by ID: {}", cartId, e);
            return null;
        }
    }

    private void scheduleAutoApproval(UUID paymentTechnicalId) {
        // In a real implementation, this would be handled by the PaymentAutoMarkPaidProcessor
        // after a 3-second delay. For demo purposes, we simulate this by immediately
        // triggering the AUTO_MARK_PAID transition
        
        new Thread(() -> {
            try {
                Thread.sleep(3000); // 3 second delay
                
                // Get current payment
                ModelSpec modelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);
                
                EntityWithMetadata<Payment> paymentWithMetadata = entityService.getById(
                    paymentTechnicalId, modelSpec, Payment.class);
                
                if (paymentWithMetadata != null && "INITIATED".equals(paymentWithMetadata.entity().getStatus())) {
                    Payment payment = paymentWithMetadata.entity();
                    payment.setStatus("PAID");
                    payment.setUpdatedAt(LocalDateTime.now());
                    
                    // Update with AUTO_MARK_PAID transition
                    entityService.update(paymentTechnicalId, payment, "AUTO_MARK_PAID");
                    
                    logger.info("Payment {} auto-approved after 3 seconds", payment.getPaymentId());
                }
            } catch (Exception e) {
                logger.error("Error in auto-approval for payment: {}", paymentTechnicalId, e);
            }
        }).start();
    }

    // Request/Response DTOs

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

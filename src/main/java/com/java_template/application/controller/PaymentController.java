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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ABOUTME: Payment controller providing REST endpoints for dummy payment
 * processing including payment start and status polling.
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
    public ResponseEntity<PaymentStartResponse> startPayment(@RequestBody PaymentStartRequest request) {
        try {
            // Validate cart exists and get amount
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found for payment: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Validate cart is in CHECKING_OUT state
            String cartState = cartWithMetadata.metadata().getState();
            if (!"checking_out".equals(cartState)) {
                logger.warn("Cart {} is not in checking_out state: {}", request.getCartId(), cartState);
                return ResponseEntity.badRequest().build();
            }

            // Create payment entity
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            // Create payment - this will trigger auto-approval after 3 seconds
            EntityWithMetadata<Payment> createdPayment = entityService.create(payment);

            // Trigger auto-approval transition
            entityService.update(createdPayment.metadata().getId(), payment, "auto_mark_paid");

            PaymentStartResponse response = new PaymentStartResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus("INITIATED");
            response.setAmount(payment.getAmount());

            logger.info("Started dummy payment {} for cart {} amount ${}", 
                       payment.getPaymentId(), request.getCartId(), payment.getAmount());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error starting payment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payment status by payment ID
     * GET /ui/payment/{paymentId}
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessIdOrNull(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            String paymentState = paymentWithMetadata.metadata().getState();

            PaymentStatusResponse response = new PaymentStatusResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setCartId(payment.getCartId());
            response.setAmount(payment.getAmount());
            response.setProvider(payment.getProvider());
            response.setStatus(mapStateToStatus(paymentState));
            response.setCreatedAt(payment.getCreatedAt());
            response.setUpdatedAt(payment.getUpdatedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment status for: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payment by technical UUID
     * GET /ui/payment/id/{id}
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> response = entityService.getById(id, modelSpec, Payment.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Cancel payment (manual transition)
     * POST /ui/payment/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentStatusResponse> cancelPayment(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessIdOrNull(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            payment.setUpdatedAt(LocalDateTime.now());

            // Cancel payment with manual transition
            EntityWithMetadata<Payment> updatedPayment = entityService.update(
                    paymentWithMetadata.metadata().getId(), payment, "mark_canceled");

            PaymentStatusResponse response = new PaymentStatusResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setCartId(payment.getCartId());
            response.setAmount(payment.getAmount());
            response.setProvider(payment.getProvider());
            response.setStatus("CANCELED");
            response.setCreatedAt(payment.getCreatedAt());
            response.setUpdatedAt(payment.getUpdatedAt());

            logger.info("Canceled payment {}", paymentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error canceling payment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private String mapStateToStatus(String state) {
        switch (state.toLowerCase()) {
            case "initiated": return "INITIATED";
            case "paid": return "PAID";
            case "failed": return "FAILED";
            case "canceled": return "CANCELED";
            default: return state.toUpperCase();
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
        private String status;
        private Double amount;
    }

    @Getter
    @Setter
    public static class PaymentStatusResponse {
        private String paymentId;
        private String cartId;
        private Double amount;
        private String provider;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}

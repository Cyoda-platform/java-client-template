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
 * Payment controller for dummy payment processing
 * Auto-approves payments after ~3 seconds for demo purposes
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
            // Validate cart exists
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);
            
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8));
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.create(payment);

            PaymentStartResponse response = new PaymentStartResponse();
            response.setPaymentId(payment.getPaymentId());

            logger.info("Payment {} started for cart {} with amount ${}", 
                       payment.getPaymentId(), request.getCartId(), payment.getAmount());

            return ResponseEntity.ok(response);
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
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable String paymentId) {
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
            String status = paymentWithMetadata.metadata().getState();

            PaymentStatusResponse response = new PaymentStatusResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setCartId(payment.getCartId());
            response.setAmount(payment.getAmount());
            response.setStatus(mapWorkflowStateToStatus(status));
            response.setProvider(payment.getProvider());
            response.setCreatedAt(payment.getCreatedAt());
            response.setUpdatedAt(payment.getUpdatedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payment by technical ID
     * GET /ui/payment/id/{id}
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);
            
            EntityWithMetadata<Payment> payment = entityService.getById(id, modelSpec, Payment.class);
            return ResponseEntity.ok(payment);
        } catch (Exception e) {
            logger.error("Error getting payment by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Cancel payment (manual operation)
     * POST /ui/payment/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentStatusResponse> cancelPayment(@PathVariable String paymentId) {
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

            // Cancel payment with manual transition
            EntityWithMetadata<Payment> updatedPayment = entityService.update(
                    paymentWithMetadata.metadata().getId(), payment, "cancel_payment");

            PaymentStatusResponse response = new PaymentStatusResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setCartId(payment.getCartId());
            response.setAmount(payment.getAmount());
            response.setStatus("CANCELED");
            response.setProvider(payment.getProvider());
            response.setCreatedAt(payment.getCreatedAt());
            response.setUpdatedAt(payment.getUpdatedAt());

            logger.info("Payment {} canceled", paymentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error canceling payment: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Map workflow state to business status
     */
    private String mapWorkflowStateToStatus(String workflowState) {
        switch (workflowState.toLowerCase()) {
            case "initial":
            case "initiated":
                return "INITIATED";
            case "paid":
                return "PAID";
            case "failed":
                return "FAILED";
            case "canceled":
                return "CANCELED";
            default:
                return workflowState.toUpperCase();
        }
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

    @Getter
    @Setter
    public static class PaymentStatusResponse {
        private String paymentId;
        private String cartId;
        private Double amount;
        private String status;
        private String provider;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}

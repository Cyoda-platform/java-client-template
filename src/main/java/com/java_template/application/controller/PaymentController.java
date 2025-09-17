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
 * PaymentController - REST endpoints for payment processing
 * 
 * Handles dummy payment creation and status polling for the demo.
 * Payments auto-approve after ~3 seconds via the AutoMarkPaidProcessor.
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
            // Validate cart exists and is in CONVERTED state
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartResponse == null) {
                logger.error("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartResponse.entity();
            String cartState = cartResponse.metadata().getState();

            // Validate cart is in CONVERTED state
            if (!"converted".equals(cartState)) {
                logger.error("Cart {} is not in converted state. Current state: {}", request.getCartId(), cartState);
                return ResponseEntity.badRequest().build();
            }

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> paymentResponse = entityService.create(payment);
            
            logger.info("Payment started: {} for cart: {} amount: {}", 
                       payment.getPaymentId(), request.getCartId(), payment.getAmount());

            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(paymentResponse.metadata().getState());
            response.setAmount(payment.getAmount());

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
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentResponse = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentResponse.entity();
            String status = paymentResponse.metadata().getState();

            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(status);
            response.setAmount(payment.getAmount());
            response.setCartId(payment.getCartId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel payment
     * POST /ui/payment/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentResponse = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentResponse.entity();
            String currentState = paymentResponse.metadata().getState();

            // Can only cancel if in INITIATED state
            if (!"initiated".equals(currentState)) {
                logger.error("Cannot cancel payment {} in state: {}", paymentId, currentState);
                return ResponseEntity.badRequest().build();
            }

            // Update payment with CANCEL_PAYMENT transition
            payment.setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<Payment> updatedResponse = entityService.update(
                    paymentResponse.metadata().getId(), payment, "CANCEL_PAYMENT");

            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(updatedResponse.metadata().getState());
            response.setAmount(payment.getAmount());
            response.setCartId(payment.getCartId());

            logger.info("Payment cancelled: {}", paymentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error cancelling payment: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Request/Response DTOs
     */
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

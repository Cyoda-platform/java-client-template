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
 * Payment controller for dummy payment processing.
 * Auto-approves payments after ~3 seconds for demo purposes.
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
     */
    @PostMapping("/start")
    public ResponseEntity<PaymentStartResponse> startPayment(@RequestBody PaymentStartRequest request) {
        logger.info("Starting payment for cart: {}", request.getCartId());

        try {
            // Get cart to validate and get amount
            ModelSpec cartModelSpec = new ModelSpec();
            cartModelSpec.setName(Cart.ENTITY_NAME);
            cartModelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart is in CHECKING_OUT status and has guest contact
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.warn("Cart {} is not in CHECKING_OUT status: {}", request.getCartId(), cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            if (cart.getGuestContact() == null) {
                logger.warn("Cart {} does not have guest contact information", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            if (cart.getGrandTotal() == null || cart.getGrandTotal() <= 0) {
                logger.warn("Cart {} has invalid grand total: {}", request.getCartId(), cart.getGrandTotal());
                return ResponseEntity.badRequest().build();
            }

            // Create payment
            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            // Save payment - this will trigger the workflow
            EntityWithMetadata<Payment> savedPayment = entityService.create(payment);

            logger.info("Payment {} started for cart {} with amount: {}", 
                       savedPayment.entity().getPaymentId(), request.getCartId(), cart.getGrandTotal());

            PaymentStartResponse response = new PaymentStartResponse();
            response.setPaymentId(savedPayment.entity().getPaymentId());
            response.setStatus(savedPayment.entity().getStatus());
            response.setAmount(savedPayment.entity().getAmount());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error starting payment for cart: {}", request.getCartId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get payment status (for polling)
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<Payment> getPaymentStatus(@PathVariable String paymentId) {
        logger.info("Getting payment status: {}", paymentId);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Payment.ENTITY_NAME);
            modelSpec.setVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                logger.warn("Payment not found: {}", paymentId);
                return ResponseEntity.notFound().build();
            }

            logger.info("Payment {} status: {}", paymentId, paymentWithMetadata.entity().getStatus());
            return ResponseEntity.ok(paymentWithMetadata.entity());

        } catch (Exception e) {
            logger.error("Error getting payment status: {}", paymentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Data
    public static class PaymentStartRequest {
        private String cartId;
    }

    @Data
    public static class PaymentStartResponse {
        private String paymentId;
        private String status;
        private Double amount;
    }
}

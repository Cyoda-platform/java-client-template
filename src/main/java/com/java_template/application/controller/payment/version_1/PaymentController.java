package com.java_template.application.controller.payment.version_1;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PaymentController handles dummy payment processing.
 */
@RestController
@RequestMapping("/ui/payments")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final EntityService entityService;

    public PaymentController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create payment for cart
     */
    @PostMapping
    public ResponseEntity<Payment> createPayment(@RequestBody Map<String, Object> requestBody) {
        logger.info("Creating payment");

        try {
            String cartId = (String) requestBody.get("cartId");
            
            if (cartId == null || cartId.trim().isEmpty()) {
                logger.warn("Cart ID is required for payment creation");
                return ResponseEntity.badRequest().build();
            }

            // Create new payment
            String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "");
            Payment payment = new Payment();
            payment.setPaymentId(paymentId);
            payment.setCartId(cartId);
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            // Prepare request data for processor
            Map<String, Object> processorData = new HashMap<>();
            processorData.put("cartId", cartId);

            // Save payment
            EntityResponse<Payment> response = entityService.save(payment);
            Payment savedPayment = response.getData();

            logger.info("Payment created successfully: paymentId={}, cartId={}", 
                savedPayment.getPaymentId(), savedPayment.getCartId());
            return ResponseEntity.ok(savedPayment);

        } catch (Exception e) {
            logger.error("Failed to create payment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Process payment (auto-approve for DUMMY provider)
     */
    @PostMapping("/{paymentId}/process")
    public ResponseEntity<Payment> processPayment(
            @PathVariable String paymentId,
            @RequestParam String transition) {

        logger.info("Processing payment: paymentId={}, transition={}", paymentId, transition);

        if (!"AUTO_APPROVE".equals(transition)) {
            logger.warn("Invalid transition for payment processing: {}", transition);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Get existing payment
            EntityResponse<Payment> paymentResponse = entityService.findByBusinessId(Payment.class, paymentId);
            Payment payment = paymentResponse.getData();
            
            if (payment == null) {
                logger.warn("Payment not found: {}", paymentId);
                return ResponseEntity.notFound().build();
            }

            // Update payment with AUTO_APPROVE transition
            EntityResponse<Payment> updatedResponse = entityService.update(
                paymentResponse.getId(),
                payment,
                "AUTO_APPROVE"
            );
            Payment updatedPayment = updatedResponse.getData();

            logger.info("Payment processed successfully: paymentId={}, amount={}", 
                updatedPayment.getPaymentId(), updatedPayment.getAmount());
            return ResponseEntity.ok(updatedPayment);

        } catch (Exception e) {
            logger.error("Failed to process payment {}: {}", paymentId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel payment
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<Payment> cancelPayment(
            @PathVariable String paymentId,
            @RequestParam String transition) {

        logger.info("Cancelling payment: paymentId={}, transition={}", paymentId, transition);

        if (!"CANCEL".equals(transition)) {
            logger.warn("Invalid transition for payment cancellation: {}", transition);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Get existing payment
            EntityResponse<Payment> paymentResponse = entityService.findByBusinessId(Payment.class, paymentId);
            Payment payment = paymentResponse.getData();
            
            if (payment == null) {
                logger.warn("Payment not found: {}", paymentId);
                return ResponseEntity.notFound().build();
            }

            // Update payment with CANCEL transition
            EntityResponse<Payment> updatedResponse = entityService.update(
                paymentResponse.getId(),
                payment,
                "CANCEL"
            );
            Payment updatedPayment = updatedResponse.getData();

            logger.info("Payment cancelled successfully: paymentId={}", updatedPayment.getPaymentId());
            return ResponseEntity.ok(updatedPayment);

        } catch (Exception e) {
            logger.error("Failed to cancel payment {}: {}", paymentId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payment details
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<Payment> getPayment(@PathVariable String paymentId) {
        logger.info("Getting payment details: paymentId={}", paymentId);

        try {
            EntityResponse<Payment> response = entityService.findByBusinessId(Payment.class, paymentId);
            Payment payment = response.getData();
            
            if (payment == null) {
                logger.warn("Payment not found: {}", paymentId);
                return ResponseEntity.notFound().build();
            }

            logger.info("Found payment: paymentId={}, cartId={}, amount={}", 
                payment.getPaymentId(), payment.getCartId(), payment.getAmount());
            return ResponseEntity.ok(payment);

        } catch (Exception e) {
            logger.error("Failed to get payment {}: {}", paymentId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

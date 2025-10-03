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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ABOUTME: Payment controller providing dummy payment processing endpoints
 * with automatic approval after 3 seconds for demo purposes.
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
    public ResponseEntity<?> startPayment(@RequestBody StartPaymentRequest request) {
        try {
            // Find cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cart not found: " + request.getCartId()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart status
            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cart must be in CHECKING_OUT status. Current status: " + cart.getStatus()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Validate cart has guest contact
            if (cart.getGuestContact() == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cart must have guest contact information before payment"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Validate cart has items and total
            if (cart.getLines() == null || cart.getLines().isEmpty() || cart.getGrandTotal() <= 0) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cart must have items and valid total before payment"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Create payment entity
            Payment payment = new Payment();
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            payment.setCartId(request.getCartId());
            payment.setAmount(cart.getGrandTotal());
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            // Create payment with workflow transition
            EntityWithMetadata<Payment> paymentResponse = entityService.create(payment);
            
            // Start auto-approval process by triggering the workflow transition
            // The AutoMarkPaidAfter3s processor will handle the 3-second delay and status change
            EntityWithMetadata<Payment> updatedPayment = entityService.update(
                paymentResponse.metadata().getId(), 
                paymentResponse.entity(), 
                "auto_mark_paid"
            );

            logger.info("Payment {} started for cart {} with amount {}", 
                       payment.getPaymentId(), request.getCartId(), payment.getAmount());

            // Return payment ID for polling
            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(payment.getStatus());
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
    public ResponseEntity<?> getPaymentStatus(@PathVariable String paymentId) {
        try {
            // Find payment by business ID
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessIdOrNull(
                    paymentModelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();

            // Return payment status
            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(payment.getStatus());
            response.setAmount(payment.getAmount());
            response.setCartId(payment.getCartId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting payment status for paymentId: {}", paymentId, e);
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
            logger.error("Error getting Payment by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Cancel payment (manual operation)
     * POST /ui/payment/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<?> cancelPayment(@PathVariable String paymentId) {
        try {
            // Find payment by business ID
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessIdOrNull(
                    paymentModelSpec, paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();

            // Validate payment can be cancelled
            if (!"INITIATED".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Payment can only be cancelled when in INITIATED status. Current status: " + payment.getStatus()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Cancel payment
            payment.setStatus("CANCELED");
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.update(
                paymentWithMetadata.metadata().getId(), 
                payment, 
                "mark_canceled"
            );

            logger.info("Payment {} cancelled", paymentId);

            PaymentResponse responseDto = new PaymentResponse();
            responseDto.setPaymentId(payment.getPaymentId());
            responseDto.setStatus(payment.getStatus());
            responseDto.setAmount(payment.getAmount());
            responseDto.setCartId(payment.getCartId());

            return ResponseEntity.ok(responseDto);

        } catch (Exception e) {
            logger.error("Error cancelling payment", e);
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

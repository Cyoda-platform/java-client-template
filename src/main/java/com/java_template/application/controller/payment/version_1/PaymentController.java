package com.java_template.application.controller.payment.version_1;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public CompletableFuture<ResponseEntity<Payment>> createPayment(@RequestBody Payment payment) {
        logger.info("Creating payment for cart: {}", payment.getCartId());

        return entityService.create(payment)
            .thenApply(createdPayment -> {
                logger.info("Payment created successfully with ID: {}", createdPayment.getPaymentId());
                return ResponseEntity.ok(createdPayment);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to create payment: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @GetMapping("/{paymentId}")
    public CompletableFuture<ResponseEntity<Payment>> getPayment(@PathVariable String paymentId) {
        logger.info("Retrieving payment with ID: {}", paymentId);

        return entityService.findById(Payment.class, paymentId)
            .thenApply(payment -> {
                if (payment != null) {
                    logger.info("Payment found with ID: {}", paymentId);
                    return ResponseEntity.ok(payment);
                } else {
                    logger.warn("Payment not found with ID: {}", paymentId);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to retrieve payment: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @GetMapping("/cart/{cartId}")
    public CompletableFuture<ResponseEntity<List<Payment>>> getPaymentsByCart(@PathVariable String cartId) {
        logger.info("Retrieving payments for cart: {}", cartId);

        return entityService.findByField(Payment.class, "cartId", cartId)
            .thenApply(payments -> {
                logger.info("Retrieved {} payments for cart: {}", payments.size(), cartId);
                return ResponseEntity.ok(payments);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to retrieve payments by cart: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @PostMapping("/{paymentId}/process")
    public CompletableFuture<ResponseEntity<Payment>> processPayment(@PathVariable String paymentId) {
        logger.info("Processing payment with ID: {}", paymentId);

        return entityService.findById(Payment.class, paymentId)
            .thenCompose(payment -> {
                if (payment == null) {
                    return CompletableFuture.completedFuture(null);
                }

                // Trigger payment processing (this will be handled by the workflow)
                return entityService.update(payment);
            })
            .thenApply(updatedPayment -> {
                if (updatedPayment != null) {
                    logger.info("Payment processing initiated successfully");
                    return ResponseEntity.ok(updatedPayment);
                } else {
                    logger.warn("Payment not found with ID: {}", paymentId);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to process payment: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @PostMapping("/{paymentId}/cancel")
    public CompletableFuture<ResponseEntity<Payment>> cancelPayment(@PathVariable String paymentId) {
        logger.info("Cancelling payment with ID: {}", paymentId);

        return entityService.findById(Payment.class, paymentId)
            .thenCompose(payment -> {
                if (payment == null) {
                    return CompletableFuture.completedFuture(null);
                }

                // Set payment status to cancelled
                payment.setStatus("CANCELLED");

                return entityService.update(payment);
            })
            .thenApply(updatedPayment -> {
                if (updatedPayment != null) {
                    logger.info("Payment cancelled successfully");
                    return ResponseEntity.ok(updatedPayment);
                } else {
                    logger.warn("Payment not found with ID: {}", paymentId);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to cancel payment: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<List<Payment>>> getAllPayments() {
        logger.info("Retrieving all payments");

        return entityService.findAll(Payment.class)
            .thenApply(payments -> {
                logger.info("Retrieved {} payments", payments.size());
                return ResponseEntity.ok(payments);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to retrieve payments: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }
}
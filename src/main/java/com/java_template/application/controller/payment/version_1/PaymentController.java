package com.java_template.application.controller.payment.version_1;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<Payment> createPayment(@RequestBody Payment payment) {
        logger.info("Creating payment with ID: {}", payment.getPaymentId());

        try {
            Payment createdPayment = entityService.create(payment);
            logger.info("Payment created successfully with ID: {}", createdPayment.getPaymentId());
            return ResponseEntity.ok(createdPayment);
        } catch (Exception e) {
            logger.error("Failed to create payment: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<Payment> getPayment(@PathVariable String paymentId) {
        logger.info("Retrieving payment with ID: {}", paymentId);

        try {
            Payment payment = entityService.findById(Payment.class, paymentId);
            if (payment != null) {
                logger.info("Payment found with ID: {}", paymentId);
                return ResponseEntity.ok(payment);
            } else {
                logger.warn("Payment not found with ID: {}", paymentId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve payment: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Payment>> getAllPayments() {
        logger.info("Retrieving all payments");

        try {
            List<Payment> payments = entityService.findAll(Payment.class);
            logger.info("Retrieved {} payments", payments.size());
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            logger.error("Failed to retrieve payments: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{paymentId}/process")
    public ResponseEntity<Payment> processPayment(@PathVariable String paymentId) {
        logger.info("Processing payment with ID: {}", paymentId);

        try {
            Payment payment = entityService.findById(Payment.class, paymentId);
            if (payment == null) {
                logger.warn("Payment not found with ID: {}", paymentId);
                return ResponseEntity.notFound().build();
            }

            // Trigger payment processing (this will be handled by the workflow)
            Payment updatedPayment = entityService.update(payment);
            
            logger.info("Payment processing initiated successfully");
            return ResponseEntity.ok(updatedPayment);
        } catch (Exception e) {
            logger.error("Failed to process payment: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<Payment> cancelPayment(@PathVariable String paymentId) {
        logger.info("Cancelling payment with ID: {}", paymentId);

        try {
            Payment payment = entityService.findById(Payment.class, paymentId);
            if (payment == null) {
                logger.warn("Payment not found with ID: {}", paymentId);
                return ResponseEntity.notFound().build();
            }

            // Set payment status to cancelled
            payment.setStatus("CANCELLED");
            Payment updatedPayment = entityService.update(payment);
            
            logger.info("Payment cancelled successfully");
            return ResponseEntity.ok(updatedPayment);
        } catch (Exception e) {
            logger.error("Failed to cancel payment: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Payment>> getPaymentsByOrder(@PathVariable String orderId) {
        logger.info("Retrieving payments for order: {}", orderId);

        try {
            List<Payment> payments = entityService.findByField(Payment.class, "orderId", orderId);
            logger.info("Retrieved {} payments for order: {}", payments.size(), orderId);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            logger.error("Failed to retrieve payments by order: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

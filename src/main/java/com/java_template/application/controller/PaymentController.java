package com.java_template.application.controller;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final EntityService entityService;

    public PaymentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<EntityResponse<Payment>> createPayment(@RequestBody Payment payment) {
        try {
            EntityResponse<Payment> response = entityService.save(payment);
            logger.info("Payment created with ID: {}", response.getMetadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating payment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<Payment>> getPayment(@PathVariable UUID id) {
        try {
            EntityResponse<Payment> response = entityService.getItem(id, Payment.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving payment with ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/paymentId/{paymentId}")
    public ResponseEntity<EntityResponse<Payment>> getPaymentByPaymentId(@PathVariable String paymentId) {
        try {
            Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(paymentIdCondition));

            Optional<EntityResponse<Payment>> response = entityService.getFirstItemByCondition(
                Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, condition, true);
            
            if (response.isPresent()) {
                return ResponseEntity.ok(response.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving payment with paymentId: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/cart/{cartId}")
    public ResponseEntity<EntityResponse<Payment>> getPaymentByCartId(@PathVariable String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Payment>> response = entityService.getFirstItemByCondition(
                Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, condition, true);
            
            if (response.isPresent()) {
                return ResponseEntity.ok(response.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving payment with cartId: {}", cartId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<Payment>>> getAllPayments() {
        try {
            List<EntityResponse<Payment>> payments = entityService.findAll(Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            logger.error("Error retrieving all payments", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<Payment>> updatePayment(
            @PathVariable UUID id, 
            @RequestBody Payment payment,
            @RequestParam(required = false) String transition) {
        try {
            EntityResponse<Payment> response = entityService.update(id, payment, transition);
            logger.info("Payment updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating payment with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayment(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Payment deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting payment with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/initiate")
    public ResponseEntity<EntityResponse<Payment>> initiatePayment(@PathVariable UUID id) {
        try {
            EntityResponse<Payment> paymentResponse = entityService.getItem(id, Payment.class);
            Payment payment = paymentResponse.getData();
            EntityResponse<Payment> response = entityService.update(id, payment, "INITIATE");
            logger.info("Payment initiated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error initiating payment with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<EntityResponse<Payment>> approvePayment(@PathVariable UUID id) {
        try {
            EntityResponse<Payment> paymentResponse = entityService.getItem(id, Payment.class);
            Payment payment = paymentResponse.getData();
            EntityResponse<Payment> response = entityService.update(id, payment, "APPROVE");
            logger.info("Payment approved with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error approving payment with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/fail")
    public ResponseEntity<EntityResponse<Payment>> failPayment(@PathVariable UUID id) {
        try {
            EntityResponse<Payment> paymentResponse = entityService.getItem(id, Payment.class);
            Payment payment = paymentResponse.getData();
            EntityResponse<Payment> response = entityService.update(id, payment, "FAIL");
            logger.info("Payment failed with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error failing payment with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<EntityResponse<Payment>> cancelPayment(@PathVariable UUID id) {
        try {
            EntityResponse<Payment> paymentResponse = entityService.getItem(id, Payment.class);
            Payment payment = paymentResponse.getData();
            EntityResponse<Payment> response = entityService.update(id, payment, "CANCEL");
            logger.info("Payment cancelled with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error cancelling payment with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}

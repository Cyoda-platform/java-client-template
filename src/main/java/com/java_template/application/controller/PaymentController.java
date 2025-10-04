package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Payment entity operations, providing endpoints
 * for recording and managing borrower payments throughout the payment lifecycle.
 */
@RestController
@RequestMapping("/ui/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PaymentController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Record a new payment
     * POST /ui/payments
     */
    @PostMapping
    public ResponseEntity<?> recordPayment(@RequestBody Payment payment) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, payment.getPaymentId(), "paymentId", Payment.class);

            if (existing != null) {
                logger.warn("Payment with business ID {} already exists", payment.getPaymentId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Payment already exists with ID: %s", payment.getPaymentId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Set received date to today if not provided
            if (payment.getReceivedDate() == null) {
                payment.setReceivedDate(LocalDate.now());
            }

            // Set value date to received date if not provided
            if (payment.getValueDate() == null) {
                payment.setValueDate(payment.getReceivedDate());
            }

            EntityWithMetadata<Payment> response = entityService.create(payment);
            logger.info("Payment recorded with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error recording Payment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payment by technical UUID
     * GET /ui/payments/{id}
     */
    @GetMapping("/{id}")
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
     * Get payment by business identifier
     * GET /ui/payments/business/{paymentId}
     */
    @GetMapping("/business/{paymentId}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentByBusinessId(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> response = entityService.findByBusinessIdOrNull(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Payment by business ID: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payments for a specific loan
     * GET /ui/payments/loan/{loanId}
     */
    @GetMapping("/loan/{loanId}")
    public ResponseEntity<List<EntityWithMetadata<Payment>>> getPaymentsForLoan(@PathVariable String loanId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);

            SimpleCondition loanCondition = new SimpleCondition()
                    .withJsonPath("$.loanId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(loanId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(loanCondition));

            List<EntityWithMetadata<Payment>> payments = entityService.search(modelSpec, condition, Payment.class);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            logger.error("Error getting payments for loan: {}", loanId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update payment with optional workflow transition
     * PUT /ui/payments/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Payment>> updatePayment(
            @PathVariable UUID id,
            @RequestBody Payment payment,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Payment> response = entityService.update(id, payment, transition);
            logger.info("Payment updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Payment: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * List all payments with optional filtering
     * GET /ui/payments?loanId=LOAN123&status=POSTED
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Payment>>> listPayments(
            @RequestParam(required = false) String loanId,
            @RequestParam(required = false) String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            
            List<SimpleCondition> conditions = new java.util.ArrayList<>();
            
            if (loanId != null && !loanId.trim().isEmpty()) {
                SimpleCondition loanCondition = new SimpleCondition()
                        .withJsonPath("$.loanId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(loanId));
                conditions.add(loanCondition);
            }

            List<EntityWithMetadata<Payment>> payments;
            if (conditions.isEmpty()) {
                payments = entityService.findAll(modelSpec, Payment.class);
            } else {
                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                payments = entityService.search(modelSpec, groupCondition, Payment.class);
            }

            // Filter by status if provided (status is in metadata, not entity)
            if (status != null && !status.trim().isEmpty()) {
                payments = payments.stream()
                        .filter(payment -> status.equals(payment.metadata().getState()))
                        .toList();
            }

            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            logger.error("Error listing payments", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Manually match payment to loan
     * POST /ui/payments/{id}/match
     */
    @PostMapping("/{id}/match")
    public ResponseEntity<EntityWithMetadata<Payment>> matchPayment(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> current = entityService.getById(id, modelSpec, Payment.class);
            
            EntityWithMetadata<Payment> response = entityService.update(id, current.entity(), "manual_match");
            logger.info("Payment manually matched with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error manually matching payment: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Return unmatched payment
     * POST /ui/payments/{id}/return
     */
    @PostMapping("/{id}/return")
    public ResponseEntity<EntityWithMetadata<Payment>> returnPayment(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> current = entityService.getById(id, modelSpec, Payment.class);
            
            EntityWithMetadata<Payment> response = entityService.update(id, current.entity(), "return_payment");
            logger.info("Payment returned with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error returning payment: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}

package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
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

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to record payment: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve payment with business ID '%s': %s", paymentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve payments for loan '%s': %s", loanId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update payment with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * List all payments with pagination and optional filtering
     * GET /ui/payments?page=0&size=20&loanId=LOAN123&status=POSTED
     */
    @GetMapping
    public ResponseEntity<?> listPayments(
            Pageable pageable,
            @RequestParam(required = false) String loanId,
            @RequestParam(required = false) String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);

            List<QueryCondition> conditions = new java.util.ArrayList<>();

            if (loanId != null && !loanId.trim().isEmpty()) {
                SimpleCondition loanCondition = new SimpleCondition()
                        .withJsonPath("$.loanId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(loanId));
                conditions.add(loanCondition);
            }

            if (conditions.isEmpty() && (status == null || status.trim().isEmpty())) {
                // Use paginated findAll when no filters
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, Payment.class));
            } else {
                // For filtered results, use search (returns all matching results, not paginated)
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
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list payments: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to manually match payment with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to return payment with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete payment by technical UUID
     * DELETE /ui/payments/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayment(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Payment deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete payment with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete payment by business identifier
     * DELETE /ui/payments/business/{paymentId}
     */
    @DeleteMapping("/business/{paymentId}")
    public ResponseEntity<Void> deletePaymentByBusinessId(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            boolean deleted = entityService.deleteByBusinessId(modelSpec, paymentId, "paymentId", Payment.class);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Payment deleted with business ID: {}", paymentId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete payment with business ID '%s': %s", paymentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete all payments (DANGEROUS - use with caution)
     * DELETE /ui/payments
     */
    @DeleteMapping
    public ResponseEntity<?> deleteAllPayments() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            Integer deletedCount = entityService.deleteAll(modelSpec);
            logger.warn("Deleted all Payments - count: {}", deletedCount);
            return ResponseEntity.ok().body(String.format("Deleted %d payments", deletedCount));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete all payments: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

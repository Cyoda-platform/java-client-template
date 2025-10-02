package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: This controller provides REST endpoints for payment management including
 * CRUD operations, workflow transitions, and search functionality.
 */
@RestController
@RequestMapping("/ui/payment")
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
     * Create a new payment
     * POST /ui/payment
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Payment>> createPayment(@RequestBody Payment payment) {
        try {
            payment.setCapturedAt(LocalDateTime.now());
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());
            payment.setSourceType("MANUAL");

            EntityWithMetadata<Payment> response = entityService.create(payment);
            logger.info("Payment created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating payment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payment by technical UUID
     * GET /ui/payment/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> response = entityService.getById(id, modelSpec, Payment.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get payment by business identifier
     * GET /ui/payment/business/{paymentId}
     */
    @GetMapping("/business/{paymentId}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentByBusinessId(@PathVariable String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> response = entityService.findByBusinessId(
                    modelSpec, paymentId, "paymentId", Payment.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment by business ID: {}", paymentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update payment with optional workflow transition
     * PUT /ui/payment/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Payment>> updatePayment(
            @PathVariable UUID id,
            @RequestBody Payment payment,
            @RequestParam(required = false) String transition) {
        try {
            payment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Payment> response = entityService.update(id, payment, transition);
            logger.info("Payment updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating payment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete payment by technical UUID
     * DELETE /ui/payment/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayment(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Payment deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting payment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all payments
     * GET /ui/payment
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Payment>>> getAllPayments() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            List<EntityWithMetadata<Payment>> payments = entityService.findAll(modelSpec, Payment.class);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            logger.error("Error getting all payments", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search payments by loan
     * GET /ui/payment/search/loan/{loanId}
     */
    @GetMapping("/search/loan/{loanId}")
    public ResponseEntity<List<EntityWithMetadata<Payment>>> getPaymentsByLoan(@PathVariable String loanId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);

            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.loanId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(loanId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<Payment>> payments = entityService.search(modelSpec, groupCondition, Payment.class);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            logger.error("Error searching payments by loan: {}", loanId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced payment search
     * POST /ui/payment/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Payment>>> advancedSearch(
            @RequestBody PaymentSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getLoanId() != null && !searchRequest.getLoanId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.loanId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getLoanId())));
            }

            if (searchRequest.getMinAmount() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.amount")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinAmount())));
            }

            if (searchRequest.getMaxAmount() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.amount")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMaxAmount())));
            }

            if (searchRequest.getValueDateFrom() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.valueDate")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getValueDateFrom())));
            }

            if (searchRequest.getValueDateTo() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.valueDate")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getValueDateTo())));
            }

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<Payment>> payments = entityService.search(modelSpec, groupCondition, Payment.class);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            logger.error("Error performing advanced payment search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced search requests
     */
    @Getter
    @Setter
    public static class PaymentSearchRequest {
        private String loanId;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private LocalDate valueDateFrom;
        private LocalDate valueDateTo;
    }
}

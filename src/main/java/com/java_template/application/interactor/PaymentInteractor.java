package com.java_template.application.interactor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Interactor for payment business logic. Handles all payment-related operations
 * including CRUD, search, and workflow transitions.
 */
@Component
public class PaymentInteractor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentInteractor.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PaymentInteractor(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    public EntityWithMetadata<Payment> createPayment(Payment payment) {
        // Validate business key is not empty
        if (payment.getPaymentId().trim().isEmpty()) {
            logger.error("Payment creation failed: paymentId cannot be empty");
            throw new IllegalArgumentException("paymentId cannot be empty");
        }

        // Check for duplicate business key
        ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
        EntityWithMetadata<Payment> existing = entityService.findByBusinessIdOrNull(
                modelSpec, payment.getPaymentId(), "paymentId", Payment.class);

        if (existing != null) {
            logger.warn("Payment with paymentId {} already exists", payment.getPaymentId());
            throw new DuplicateEntityException("Payment with paymentId '" + payment.getPaymentId() + "' already exists");
        }

        payment.setCapturedAt(LocalDateTime.now());
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        payment.setSourceType("MANUAL");

        EntityWithMetadata<Payment> response = entityService.create(payment);
        logger.info("Payment created with ID: {}", response.metadata().getId());
        return response;
    }

    public EntityWithMetadata<Payment> getPaymentById(UUID id) {
        ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
        EntityWithMetadata<Payment> response = entityService.getById(id, modelSpec, Payment.class);
        logger.debug("Retrieved payment by ID: {}", id);
        return response;
    }

    public EntityWithMetadata<Payment> getPaymentByBusinessId(String paymentId) {
        ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
        EntityWithMetadata<Payment> response = entityService.findByBusinessId(
                modelSpec, paymentId, "paymentId", Payment.class);

        if (response == null) {
            logger.warn("Payment not found with business ID: {}", paymentId);
            throw new EntityNotFoundException("Payment not found with paymentId: " + paymentId);
        }
        
        logger.debug("Retrieved payment by business ID: {}", paymentId);
        return response;
    }

    public EntityWithMetadata<Payment> updatePaymentById(UUID id, Payment payment, String transition) {
        payment.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<Payment> response = entityService.update(id, payment, transition);
        logger.info("Payment updated with ID: {}", id);
        return response;
    }

    public EntityWithMetadata<Payment> updatePaymentByBusinessId(String paymentId, Payment payment, String transition) {
        payment.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<Payment> response = entityService.updateByBusinessId(payment, "paymentId", transition);
        logger.info("Payment updated with business ID: {}", paymentId);
        return response;
    }

    public void deletePayment(UUID id) {
        entityService.deleteById(id);
        logger.info("Payment deleted with ID: {}", id);
    }

    public List<EntityWithMetadata<Payment>> getAllPayments() {
        ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
        List<EntityWithMetadata<Payment>> payments = entityService.findAll(modelSpec, Payment.class);
        logger.debug("Retrieved {} payments", payments.size());
        return payments;
    }

    public List<EntityWithMetadata<Payment>> getPaymentsByLoan(String loanId) {
        ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);

        SimpleCondition condition = new SimpleCondition()
                .withJsonPath("$.loanId")
                .withOperation(org.cyoda.cloud.api.event.common.condition.Operation.EQUALS)
                .withValue(objectMapper.valueToTree(loanId));

        GroupCondition groupCondition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(condition));

        List<EntityWithMetadata<Payment>> payments = entityService.search(modelSpec, groupCondition, Payment.class);
        logger.debug("Found {} payments for loan: {}", payments.size(), loanId);
        return payments;
    }

    public List<EntityWithMetadata<Payment>> advancedSearch(PaymentSearchCriteria criteria) {
        ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);

        List<SimpleCondition> conditions = new ArrayList<>();

        if (criteria.getLoanId() != null && !criteria.getLoanId().trim().isEmpty()) {
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.loanId")
                    .withOperation(org.cyoda.cloud.api.event.common.condition.Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(criteria.getLoanId())));
        }

        if (criteria.getMinAmount() != null) {
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.amount")
                    .withOperation(org.cyoda.cloud.api.event.common.condition.Operation.GREATER_OR_EQUAL)
                    .withValue(objectMapper.valueToTree(criteria.getMinAmount())));
        }

        if (criteria.getMaxAmount() != null) {
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.amount")
                    .withOperation(org.cyoda.cloud.api.event.common.condition.Operation.LESS_OR_EQUAL)
                    .withValue(objectMapper.valueToTree(criteria.getMaxAmount())));
        }

        if (criteria.getValueDateFrom() != null) {
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.valueDate")
                    .withOperation(org.cyoda.cloud.api.event.common.condition.Operation.GREATER_OR_EQUAL)
                    .withValue(objectMapper.valueToTree(criteria.getValueDateFrom())));
        }

        if (criteria.getValueDateTo() != null) {
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.valueDate")
                    .withOperation(org.cyoda.cloud.api.event.common.condition.Operation.LESS_OR_EQUAL)
                    .withValue(objectMapper.valueToTree(criteria.getValueDateTo())));
        }

        GroupCondition groupCondition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(new ArrayList<>(conditions));

        List<EntityWithMetadata<Payment>> payments = entityService.search(modelSpec, groupCondition, Payment.class);
        logger.debug("Advanced search found {} payments", payments.size());
        return payments;
    }

    /**
     * Search criteria for advanced payment search
     */
    public static class PaymentSearchCriteria {
        private String loanId;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private LocalDate valueDateFrom;
        private LocalDate valueDateTo;

        public String getLoanId() { return loanId; }
        public void setLoanId(String loanId) { this.loanId = loanId; }

        public BigDecimal getMinAmount() { return minAmount; }
        public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }

        public BigDecimal getMaxAmount() { return maxAmount; }
        public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }

        public LocalDate getValueDateFrom() { return valueDateFrom; }
        public void setValueDateFrom(LocalDate valueDateFrom) { this.valueDateFrom = valueDateFrom; }

        public LocalDate getValueDateTo() { return valueDateTo; }
        public void setValueDateTo(LocalDate valueDateTo) { this.valueDateTo = valueDateTo; }
    }

    /**
     * Exception thrown when attempting to create a duplicate entity
     */
    public static class DuplicateEntityException extends RuntimeException {
        public DuplicateEntityException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when an entity is not found
     */
    public static class EntityNotFoundException extends RuntimeException {
        public EntityNotFoundException(String message) {
            super(message);
        }
    }
}


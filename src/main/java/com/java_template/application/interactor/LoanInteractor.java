package com.java_template.application.interactor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Interactor for loan business logic. Handles all loan-related operations
 * including CRUD, search, and workflow transitions.
 */
@Component
public class LoanInteractor {

    private static final Logger logger = LoggerFactory.getLogger(LoanInteractor.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public LoanInteractor(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    public EntityWithMetadata<Loan> createLoan(Loan loan) {
        // Validate business key is not empty
        if (loan.getLoanId().trim().isEmpty()) {
            logger.error("Loan creation failed: loanId cannot be empty");
            throw new IllegalArgumentException("loanId cannot be empty");
        }

        // Check for duplicate business key
        ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
        EntityWithMetadata<Loan> existing = entityService.findByBusinessIdOrNull(
                modelSpec, loan.getLoanId(), "loanId", Loan.class);

        if (existing != null) {
            logger.warn("Loan with loanId {} already exists", loan.getLoanId());
            throw new DuplicateEntityException("Loan with loanId '" + loan.getLoanId() + "' already exists");
        }

        loan.setCreatedAt(LocalDateTime.now());
        loan.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<Loan> response = entityService.create(loan);
        logger.info("Loan created with ID: {}", response.metadata().getId());
        return response;
    }

    public EntityWithMetadata<Loan> getLoanById(UUID id) {
        ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
        EntityWithMetadata<Loan> response = entityService.getById(id, modelSpec, Loan.class);
        logger.debug("Retrieved loan by ID: {}", id);
        return response;
    }

    public EntityWithMetadata<Loan> getLoanByBusinessId(String loanId) {
        ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
        EntityWithMetadata<Loan> response = entityService.findByBusinessId(
                modelSpec, loanId, "loanId", Loan.class);

        if (response == null) {
            logger.warn("Loan not found with business ID: {}", loanId);
            throw new EntityNotFoundException("Loan not found with loanId: " + loanId);
        }
        
        logger.debug("Retrieved loan by business ID: {}", loanId);
        return response;
    }

    public EntityWithMetadata<Loan> updateLoanById(UUID id, Loan loan, String transition) {
        loan.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<Loan> response = entityService.update(id, loan, transition);
        logger.info("Loan updated with ID: {}", id);
        return response;
    }

    public EntityWithMetadata<Loan> updateLoanByBusinessId(String loanId, Loan loan, String transition) {
        loan.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<Loan> response = entityService.updateByBusinessId(loan, "loanId", transition);
        logger.info("Loan updated with business ID: {}", loanId);
        return response;
    }

    public void deleteLoan(UUID id) {
        entityService.deleteById(id);
        logger.info("Loan deleted with ID: {}", id);
    }

    public List<EntityWithMetadata<Loan>> getAllLoans() {
        ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
        List<EntityWithMetadata<Loan>> loans = entityService.findAll(modelSpec, Loan.class);
        logger.debug("Retrieved {} loans", loans.size());
        return loans;
    }

    public List<EntityWithMetadata<Loan>> getLoansByParty(String partyId) {
        ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);

        SimpleCondition condition = new SimpleCondition()
                .withJsonPath("$.partyId")
                .withOperation(org.cyoda.cloud.api.event.common.condition.Operation.EQUALS)
                .withValue(objectMapper.valueToTree(partyId));

        GroupCondition groupCondition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(condition));

        List<EntityWithMetadata<Loan>> loans = entityService.search(modelSpec, groupCondition, Loan.class);
        logger.debug("Found {} loans for party: {}", loans.size(), partyId);
        return loans;
    }

    public List<EntityWithMetadata<Loan>> advancedSearch(LoanSearchCriteria criteria) {
        ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);

        List<SimpleCondition> conditions = new ArrayList<>();

        if (criteria.getPartyId() != null && !criteria.getPartyId().trim().isEmpty()) {
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.partyId")
                    .withOperation(org.cyoda.cloud.api.event.common.condition.Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(criteria.getPartyId())));
        }

        if (criteria.getMinPrincipal() != null) {
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.principalAmount")
                    .withOperation(org.cyoda.cloud.api.event.common.condition.Operation.GREATER_OR_EQUAL)
                    .withValue(objectMapper.valueToTree(criteria.getMinPrincipal())));
        }

        if (criteria.getMaxPrincipal() != null) {
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.principalAmount")
                    .withOperation(org.cyoda.cloud.api.event.common.condition.Operation.LESS_OR_EQUAL)
                    .withValue(objectMapper.valueToTree(criteria.getMaxPrincipal())));
        }

        if (criteria.getTermMonths() != null) {
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.termMonths")
                    .withOperation(org.cyoda.cloud.api.event.common.condition.Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(criteria.getTermMonths())));
        }

        GroupCondition groupCondition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(new ArrayList<>(conditions));

        List<EntityWithMetadata<Loan>> loans = entityService.search(modelSpec, groupCondition, Loan.class);
        logger.debug("Advanced search found {} loans", loans.size());
        return loans;
    }

    public EntityWithMetadata<Loan> approveLoan(UUID id) {
        ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
        EntityWithMetadata<Loan> loanResponse = entityService.getById(id, modelSpec, Loan.class);
        
        Loan loan = loanResponse.entity();
        loan.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<Loan> response = entityService.update(id, loan, "approve_loan");
        logger.info("Loan approved with ID: {}", id);
        return response;
    }

    public EntityWithMetadata<Loan> fundLoan(UUID id) {
        ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
        EntityWithMetadata<Loan> loanResponse = entityService.getById(id, modelSpec, Loan.class);
        
        Loan loan = loanResponse.entity();
        loan.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<Loan> response = entityService.update(id, loan, "fund_loan");
        logger.info("Loan funded with ID: {}", id);
        return response;
    }

    /**
     * Search criteria for advanced loan search
     */
    public static class LoanSearchCriteria {
        private String partyId;
        private BigDecimal minPrincipal;
        private BigDecimal maxPrincipal;
        private Integer termMonths;

        public String getPartyId() { return partyId; }
        public void setPartyId(String partyId) { this.partyId = partyId; }

        public BigDecimal getMinPrincipal() { return minPrincipal; }
        public void setMinPrincipal(BigDecimal minPrincipal) { this.minPrincipal = minPrincipal; }

        public BigDecimal getMaxPrincipal() { return maxPrincipal; }
        public void setMaxPrincipal(BigDecimal maxPrincipal) { this.maxPrincipal = maxPrincipal; }

        public Integer getTermMonths() { return termMonths; }
        public void setTermMonths(Integer termMonths) { this.termMonths = termMonths; }
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


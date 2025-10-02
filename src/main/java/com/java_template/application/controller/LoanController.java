package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.loan.version_1.Loan;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: This controller provides REST endpoints for loan management including
 * CRUD operations, workflow transitions, and search functionality.
 */
@RestController
@RequestMapping("/ui/loan")
@CrossOrigin(origins = "*")
public class LoanController {

    private static final Logger logger = LoggerFactory.getLogger(LoanController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public LoanController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new loan
     * POST /ui/loan
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Loan>> createLoan(@RequestBody Loan loan) {
        try {
            loan.setCreatedAt(LocalDateTime.now());
            loan.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Loan> response = entityService.create(loan);
            logger.info("Loan created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating loan", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get loan by technical UUID
     * GET /ui/loan/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Loan>> getLoanById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> response = entityService.getById(id, modelSpec, Loan.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting loan by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get loan by business identifier
     * GET /ui/loan/business/{loanId}
     */
    @GetMapping("/business/{loanId}")
    public ResponseEntity<EntityWithMetadata<Loan>> getLoanByBusinessId(@PathVariable String loanId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> response = entityService.findByBusinessId(
                    modelSpec, loanId, "loanId", Loan.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting loan by business ID: {}", loanId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update loan with optional workflow transition
     * PUT /ui/loan/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Loan>> updateLoan(
            @PathVariable UUID id,
            @RequestBody Loan loan,
            @RequestParam(required = false) String transition) {
        try {
            loan.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Loan> response = entityService.update(id, loan, transition);
            logger.info("Loan updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating loan", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete loan by technical UUID
     * DELETE /ui/loan/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLoan(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Loan deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting loan", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all loans
     * GET /ui/loan
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Loan>>> getAllLoans() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            List<EntityWithMetadata<Loan>> loans = entityService.findAll(modelSpec, Loan.class);
            return ResponseEntity.ok(loans);
        } catch (Exception e) {
            logger.error("Error getting all loans", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search loans by party
     * GET /ui/loan/search/party/{partyId}
     */
    @GetMapping("/search/party/{partyId}")
    public ResponseEntity<List<EntityWithMetadata<Loan>>> getLoansByParty(@PathVariable String partyId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);

            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.partyId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(partyId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<Loan>> loans = entityService.search(modelSpec, groupCondition, Loan.class);
            return ResponseEntity.ok(loans);
        } catch (Exception e) {
            logger.error("Error searching loans by party: {}", partyId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced loan search
     * POST /ui/loan/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Loan>>> advancedSearch(
            @RequestBody LoanSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getPartyId() != null && !searchRequest.getPartyId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.partyId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getPartyId())));
            }

            if (searchRequest.getMinPrincipal() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.principalAmount")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinPrincipal())));
            }

            if (searchRequest.getMaxPrincipal() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.principalAmount")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMaxPrincipal())));
            }

            if (searchRequest.getTermMonths() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.termMonths")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getTermMonths())));
            }

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<Loan>> loans = entityService.search(modelSpec, groupCondition, Loan.class);
            return ResponseEntity.ok(loans);
        } catch (Exception e) {
            logger.error("Error performing advanced loan search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Approve loan
     * POST /ui/loan/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<EntityWithMetadata<Loan>> approveLoan(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> loanResponse = entityService.getById(id, modelSpec, Loan.class);
            
            Loan loan = loanResponse.entity();
            loan.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Loan> response = entityService.update(id, loan, "approve_loan");
            logger.info("Loan approved with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error approving loan", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Fund loan
     * POST /ui/loan/{id}/fund
     */
    @PostMapping("/{id}/fund")
    public ResponseEntity<EntityWithMetadata<Loan>> fundLoan(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> loanResponse = entityService.getById(id, modelSpec, Loan.class);
            
            Loan loan = loanResponse.entity();
            loan.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Loan> response = entityService.update(id, loan, "fund_loan");
            logger.info("Loan funded with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error funding loan", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced search requests
     */
    @Getter
    @Setter
    public static class LoanSearchRequest {
        private String partyId;
        private BigDecimal minPrincipal;
        private BigDecimal maxPrincipal;
        private Integer termMonths;
    }
}

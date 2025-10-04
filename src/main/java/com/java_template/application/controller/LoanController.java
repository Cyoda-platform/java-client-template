package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.loan.version_1.Loan;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Loan entity operations, providing CRUD endpoints
 * and business actions for managing commercial loans throughout their lifecycle.
 */
@RestController
@RequestMapping("/ui/loans")
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
     * POST /ui/loans
     */
    @PostMapping
    public ResponseEntity<?> createLoan(@RequestBody Loan loan) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, loan.getLoanId(), "loanId", Loan.class);

            if (existing != null) {
                logger.warn("Loan with business ID {} already exists", loan.getLoanId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Loan already exists with ID: %s", loan.getLoanId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Calculate maturity date if not provided
            if (loan.getMaturityDate() == null && loan.getFundingDate() != null && loan.getTermMonths() != null) {
                loan.setMaturityDate(loan.getFundingDate().plusMonths(loan.getTermMonths()));
            }

            EntityWithMetadata<Loan> response = entityService.create(loan);
            logger.info("Loan created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Loan", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get loan by technical UUID
     * GET /ui/loans/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Loan>> getLoanById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> response = entityService.getById(id, modelSpec, Loan.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Loan by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get loan by business identifier
     * GET /ui/loans/business/{loanId}
     */
    @GetMapping("/business/{loanId}")
    public ResponseEntity<EntityWithMetadata<Loan>> getLoanByBusinessId(@PathVariable String loanId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> response = entityService.findByBusinessIdOrNull(
                    modelSpec, loanId, "loanId", Loan.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Loan by business ID: {}", loanId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update loan with optional workflow transition
     * PUT /ui/loans/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Loan>> updateLoan(
            @PathVariable UUID id,
            @RequestBody Loan loan,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Loan> response = entityService.update(id, loan, transition);
            logger.info("Loan updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Loan: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * List all loans with optional filtering
     * GET /ui/loans?state=ACTIVE&partyId=PARTY123
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Loan>>> listLoans(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String partyId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            
            List<SimpleCondition> conditions = new ArrayList<>();
            
            if (partyId != null && !partyId.trim().isEmpty()) {
                SimpleCondition partyCondition = new SimpleCondition()
                        .withJsonPath("$.partyId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(partyId));
                conditions.add(partyCondition);
            }

            List<EntityWithMetadata<Loan>> loans;
            if (conditions.isEmpty()) {
                loans = entityService.findAll(modelSpec, Loan.class);
            } else {
                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                loans = entityService.search(modelSpec, groupCondition, Loan.class);
            }

            // Filter by state if provided (state is in metadata, not entity)
            if (state != null && !state.trim().isEmpty()) {
                loans = loans.stream()
                        .filter(loan -> state.equals(loan.metadata().getState()))
                        .toList();
            }

            return ResponseEntity.ok(loans);
        } catch (Exception e) {
            logger.error("Error listing loans", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Submit loan for approval
     * POST /ui/loans/{id}/submit-for-approval
     */
    @PostMapping("/{id}/submit-for-approval")
    public ResponseEntity<EntityWithMetadata<Loan>> submitForApproval(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> current = entityService.getById(id, modelSpec, Loan.class);
            
            EntityWithMetadata<Loan> response = entityService.update(id, current.entity(), "submit_for_approval");
            logger.info("Loan submitted for approval with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error submitting loan for approval: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Approve loan
     * POST /ui/loans/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<EntityWithMetadata<Loan>> approveLoan(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> current = entityService.getById(id, modelSpec, Loan.class);
            
            EntityWithMetadata<Loan> response = entityService.update(id, current.entity(), "approve_loan");
            logger.info("Loan approved with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error approving loan: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reject loan
     * POST /ui/loans/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<EntityWithMetadata<Loan>> rejectLoan(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> current = entityService.getById(id, modelSpec, Loan.class);
            
            EntityWithMetadata<Loan> response = entityService.update(id, current.entity(), "reject_loan");
            logger.info("Loan rejected with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error rejecting loan: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Fund loan
     * POST /ui/loans/{id}/fund
     */
    @PostMapping("/{id}/fund")
    public ResponseEntity<EntityWithMetadata<Loan>> fundLoan(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> current = entityService.getById(id, modelSpec, Loan.class);
            
            EntityWithMetadata<Loan> response = entityService.update(id, current.entity(), "fund_loan");
            logger.info("Loan funded with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error funding loan: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate settlement quote
     * POST /ui/loans/{id}/settlement-quote
     */
    @PostMapping("/{id}/settlement-quote")
    public ResponseEntity<EntityWithMetadata<Loan>> generateSettlementQuote(
            @PathVariable UUID id,
            @RequestParam LocalDate settlementDate) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> current = entityService.getById(id, modelSpec, Loan.class);
            
            // Note: In a real implementation, you would pass the settlement date to the processor
            EntityWithMetadata<Loan> response = entityService.update(id, current.entity(), "generate_settlement_quote");
            logger.info("Settlement quote generated for loan ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error generating settlement quote for loan: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}

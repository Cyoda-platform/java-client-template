package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.loan.version_1.Loan;
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

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create loan: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve loan with business ID '%s': %s", loanId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update loan with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * List all loans with pagination and optional filtering
     * GET /ui/loans?page=0&size=20&state=ACTIVE&partyId=PARTY123
     */
    @GetMapping
    public ResponseEntity<?> listLoans(
            Pageable pageable,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String partyId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (partyId != null && !partyId.trim().isEmpty()) {
                SimpleCondition partyCondition = new SimpleCondition()
                        .withJsonPath("$.partyId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(partyId));
                conditions.add(partyCondition);
            }

            if (conditions.isEmpty() && (state == null || state.trim().isEmpty())) {
                // Use paginated findAll when no filters
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, Loan.class));
            } else {
                // For filtered results, use search (returns all matching results, not paginated)
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
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list loans: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to submit loan for approval with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to approve loan with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to reject loan with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to fund loan with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to generate settlement quote for loan with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete loan by technical UUID
     * DELETE /ui/loans/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLoan(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Loan deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete loan with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete loan by business identifier
     * DELETE /ui/loans/business/{loanId}
     */
    @DeleteMapping("/business/{loanId}")
    public ResponseEntity<Void> deleteLoanByBusinessId(@PathVariable String loanId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            boolean deleted = entityService.deleteByBusinessId(modelSpec, loanId, "loanId", Loan.class);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Loan deleted with business ID: {}", loanId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete loan with business ID '%s': %s", loanId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete all loans (DANGEROUS - use with caution)
     * DELETE /ui/loans
     */
    @DeleteMapping
    public ResponseEntity<?> deleteAllLoans() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            Integer deletedCount = entityService.deleteAll(modelSpec);
            logger.warn("Deleted all Loans - count: {}", deletedCount);
            return ResponseEntity.ok().body(String.format("Deleted %d loans", deletedCount));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete all loans: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

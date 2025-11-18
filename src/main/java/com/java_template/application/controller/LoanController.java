package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.CyodaExceptionUtil;
import jakarta.validation.Valid;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for loan management operations.
 * Provides CRUD endpoints and workflow transition operations for corporate loans.
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
    public ResponseEntity<EntityWithMetadata<Loan>> createLoan(@Valid @RequestBody Loan loan) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            EntityWithMetadata<Loan> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, loan.getLoanId(), "loanId", Loan.class);

            if (existing != null) {
                logger.warn("Loan with ID {} already exists", loan.getLoanId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Loan already exists with ID: %s", loan.getLoanId())
                );
                return ResponseEntity.of(problemDetail).build();
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
     * GET /ui/loan/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Loan>> getLoanById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Loan> response = entityService.getById(id, modelSpec, Loan.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve loan with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get loan by business identifier
     * GET /ui/loan/business/{loanId}
     */
    @GetMapping("/business/{loanId}")
    public ResponseEntity<EntityWithMetadata<Loan>> getLoanByBusinessId(
            @PathVariable String loanId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Loan> response = entityService.findByBusinessId(
                    modelSpec, loanId, "loanId", Loan.class, pointInTimeDate);

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
     * Update loan
     * PUT /ui/loan/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Loan>> updateLoan(
            @PathVariable UUID id,
            @Valid @RequestBody Loan loan,
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
     * List all loans with pagination
     * GET /ui/loan?page=0&size=20&state=ACTIVE
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Loan>>> listLoans(
            Pageable pageable,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            if (state == null || state.trim().isEmpty()) {
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, Loan.class, pointInTimeDate));
            } else {
                List<EntityWithMetadata<Loan>> entities = entityService.findAll(modelSpec, Loan.class, pointInTimeDate);
                entities = entities.stream()
                        .filter(entity -> state.equals(entity.metadata().getState()))
                        .toList();

                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), entities.size());
                List<EntityWithMetadata<Loan>> pageContent = start < entities.size()
                    ? entities.subList(start, end)
                    : new ArrayList<>();

                Page<EntityWithMetadata<Loan>> page = new PageImpl<>(pageContent, pageable, entities.size());
                return ResponseEntity.ok(page);
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
     * Search loans by borrower name
     * GET /ui/loan/search?borrowerName=text
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Loan>>> searchLoansByBorrower(
            @RequestParam String borrowerName,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.borrowerName")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(borrowerName));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Loan>> entities = entityService.search(modelSpec, condition, Loan.class, pointInTimeDate);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search loans: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete loan with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete loan by business identifier
     * DELETE /ui/loan/business/{loanId}
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
}


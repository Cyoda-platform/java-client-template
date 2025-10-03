package com.java_template.application.controller;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.application.interactor.LoanInteractor;
import com.java_template.common.dto.EntityWithMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: This controller provides REST endpoints for loan management including
 * CRUD operations, workflow transitions, and search functionality.
 * All business logic is delegated to LoanInteractor.
 */
@RestController
@RequestMapping("/api/v1/loan")
@CrossOrigin(origins = "*")
@Tag(name = "Loan Management", description = "APIs for managing commercial loans including CRUD operations, workflow transitions, and search functionality")
public class LoanController {

    private static final Logger logger = LoggerFactory.getLogger(LoanController.class);
    private final LoanInteractor loanInteractor;

    public LoanController(LoanInteractor loanInteractor) {
        this.loanInteractor = loanInteractor;
    }

    /**
     * Create a new loan
     * POST /ui/loan
     */
    @Operation(
        summary = "Create a new loan",
        description = "Creates a new loan entity. Validates that the loanId is unique before creation."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Loan created successfully",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "409", description = "Loan with the same loanId already exists",
            content = @Content(schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Invalid loan data or creation failed")
    })
    @PostMapping
    public ResponseEntity<?> createLoan(
        @Parameter(description = "Loan entity to create", required = true)
        @RequestBody Loan loan) {
        try {
            EntityWithMetadata<Loan> response = loanInteractor.createLoan(loan);
            return ResponseEntity.status(201).body(response);
        } catch (LoanInteractor.DuplicateEntityException e) {
            logger.warn("Duplicate loan creation attempt: {}", e.getMessage());
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating loan", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get loan by technical UUID
     * GET /ui/loan/{id}
     */
    @Operation(
        summary = "Get loan by technical ID",
        description = "Retrieves a loan entity by its technical UUID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Loan found",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "404", description = "Loan not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Loan>> getLoanById(
        @Parameter(description = "Technical UUID of the loan", required = true)
        @PathVariable UUID id) {
        try {
            EntityWithMetadata<Loan> response = loanInteractor.getLoanById(id);
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
    @Operation(
        summary = "Get loan by business ID",
        description = "Retrieves a loan entity by its business identifier (loanId)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Loan found",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "404", description = "Loan not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @GetMapping("/business/{loanId}")
    public ResponseEntity<EntityWithMetadata<Loan>> getLoanByBusinessId(
        @Parameter(description = "Business identifier of the loan", required = true)
        @PathVariable String loanId) {
        try {
            EntityWithMetadata<Loan> response = loanInteractor.getLoanByBusinessId(loanId);
            return ResponseEntity.ok(response);
        } catch (LoanInteractor.EntityNotFoundException e) {
            logger.warn("Loan not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error getting loan by business ID: {}", loanId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update loan by business identifier
     * PUT /ui/loan/business/{loanId}?transition=TRANSITION_NAME
     */
    @Operation(
        summary = "Update loan by business ID",
        description = "Updates a loan entity by its business identifier with optional workflow transition"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Loan updated successfully",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "404", description = "Loan not found",
            content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PutMapping("/business/{loanId}")
    public ResponseEntity<?> updateLoanByBusinessId(
            @Parameter(description = "Business identifier of the loan", required = true)
            @PathVariable String loanId,
            @Parameter(description = "Updated loan entity", required = true)
            @RequestBody Loan loan,
            @Parameter(description = "Optional workflow transition name")
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Loan> response = loanInteractor.updateLoanByBusinessId(loanId, loan, transition);
            return ResponseEntity.ok(response);
        } catch (LoanInteractor.EntityNotFoundException e) {
            logger.warn("Loan not found for update: {}", e.getMessage());
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error updating loan by business ID: {}", loanId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update loan with optional workflow transition
     * PUT /ui/loan/{id}?transition=TRANSITION_NAME
     */
    @Operation(
        summary = "Update loan by technical ID",
        description = "Updates a loan entity by its technical UUID with optional workflow transition"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Loan updated successfully",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "400", description = "Invalid loan data or update failed")
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Loan>> updateLoan(
            @Parameter(description = "Technical UUID of the loan", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Updated loan entity", required = true)
            @RequestBody Loan loan,
            @Parameter(description = "Optional workflow transition name")
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Loan> response = loanInteractor.updateLoanById(id, loan, transition);
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
    @Operation(
        summary = "Delete loan",
        description = "Deletes a loan entity by its technical UUID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Loan deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Delete operation failed")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLoan(
        @Parameter(description = "Technical UUID of the loan", required = true)
        @PathVariable UUID id) {
        try {
            loanInteractor.deleteLoan(id);
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
    @Operation(
        summary = "Get all loans",
        description = "Retrieves all loan entities. Use sparingly as this can be slow for large datasets."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Loans retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Retrieval failed")
    })
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Loan>>> getAllLoans() {
        try {
            List<EntityWithMetadata<Loan>> loans = loanInteractor.getAllLoans();
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
    @Operation(
        summary = "Search loans by party",
        description = "Retrieves all loans associated with a specific party ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Loans retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Search failed")
    })
    @GetMapping("/search/party/{partyId}")
    public ResponseEntity<List<EntityWithMetadata<Loan>>> getLoansByParty(
        @Parameter(description = "Party ID to search for", required = true)
        @PathVariable String partyId) {
        try {
            List<EntityWithMetadata<Loan>> loans = loanInteractor.getLoansByParty(partyId);
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
    @Operation(
        summary = "Advanced loan search",
        description = "Performs advanced search on loans with multiple filter criteria including party ID, principal amount range, and term months"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search completed successfully"),
        @ApiResponse(responseCode = "400", description = "Search failed")
    })
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Loan>>> advancedSearch(
            @Parameter(description = "Search criteria for loans", required = true)
            @RequestBody LoanSearchRequest searchRequest) {
        try {
            LoanInteractor.LoanSearchCriteria criteria = new LoanInteractor.LoanSearchCriteria();
            criteria.setPartyId(searchRequest.getPartyId());
            criteria.setMinPrincipal(searchRequest.getMinPrincipal());
            criteria.setMaxPrincipal(searchRequest.getMaxPrincipal());
            criteria.setTermMonths(searchRequest.getTermMonths());

            List<EntityWithMetadata<Loan>> loans = loanInteractor.advancedSearch(criteria);
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
    @Operation(
        summary = "Approve loan",
        description = "Triggers the approve_loan workflow transition for the specified loan"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Loan approved successfully",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "400", description = "Approval failed")
    })
    @PostMapping("/{id}/approve")
    public ResponseEntity<EntityWithMetadata<Loan>> approveLoan(
        @Parameter(description = "Technical UUID of the loan", required = true)
        @PathVariable UUID id) {
        try {
            EntityWithMetadata<Loan> response = loanInteractor.approveLoan(id);
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
    @Operation(
        summary = "Fund loan",
        description = "Triggers the fund_loan workflow transition for the specified loan"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Loan funded successfully",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "400", description = "Funding failed")
    })
    @PostMapping("/{id}/fund")
    public ResponseEntity<EntityWithMetadata<Loan>> fundLoan(
        @Parameter(description = "Technical UUID of the loan", required = true)
        @PathVariable UUID id) {
        try {
            EntityWithMetadata<Loan> response = loanInteractor.fundLoan(id);
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
    @Schema(description = "Search criteria for advanced loan search")
    public static class LoanSearchRequest {
        @Schema(description = "Party ID to filter by", example = "PARTY-001")
        private String partyId;

        @Schema(description = "Minimum principal amount", example = "10000.00")
        private BigDecimal minPrincipal;

        @Schema(description = "Maximum principal amount", example = "100000.00")
        private BigDecimal maxPrincipal;

        @Schema(description = "Loan term in months (12, 24, or 36)", example = "24")
        private Integer termMonths;
    }
}

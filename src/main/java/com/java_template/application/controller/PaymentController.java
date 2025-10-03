package com.java_template.application.controller;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.interactor.PaymentInteractor;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.util.CyodaExceptionUtil;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: This controller provides REST endpoints for payment management including
 * CRUD operations, workflow transitions, and search functionality.
 * All business logic is delegated to PaymentInteractor.
 */
@RestController
@RequestMapping("/api/v1/payment")
@CrossOrigin(origins = "*")
@Tag(name = "Payment Management", description = "APIs for managing borrower payments including capture, matching, allocation, and posting")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentInteractor paymentInteractor;

    public PaymentController(PaymentInteractor paymentInteractor) {
        this.paymentInteractor = paymentInteractor;
    }

    /**
     * Create a new payment
     */
    @Operation(
        summary = "Create a new payment",
        description = "Creates a new payment entity. Validates that the paymentId is unique before creation."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Payment created successfully",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "409", description = "Payment with the same paymentId already exists",
            content = @Content(schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Invalid payment data or creation failed")
    })
    @PostMapping
    public ResponseEntity<?> createPayment(
        @Parameter(description = "Payment entity to create", required = true)
        @RequestBody Payment payment) {
        try {
            EntityWithMetadata<Payment> response = paymentInteractor.createPayment(payment);
            return ResponseEntity.status(201).body(response);
        } catch (PaymentInteractor.DuplicateEntityException e) {
            logger.warn("Duplicate payment creation attempt: {}", e.getMessage());
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating payment", e);
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);
            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    /**
     * Get payment by technical UUID
     */
    @Operation(
        summary = "Get payment by technical ID",
        description = "Retrieves a payment entity by its technical UUID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment found",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentById(
        @Parameter(description = "Technical UUID of the payment", required = true)
        @PathVariable UUID id) {
        try {
            EntityWithMetadata<Payment> response = paymentInteractor.getPaymentById(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get payment by business identifier
     */
    @Operation(
        summary = "Get payment by business ID",
        description = "Retrieves a payment entity by its business identifier (paymentId)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment found",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "404", description = "Payment not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @GetMapping("/business/{paymentId}")
    public ResponseEntity<?> getPaymentByBusinessId(
        @Parameter(description = "Business identifier of the payment", required = true)
        @PathVariable String paymentId) {
        try {
            EntityWithMetadata<Payment> response = paymentInteractor.getPaymentByBusinessId(paymentId);
            return ResponseEntity.ok(response);
        } catch (PaymentInteractor.EntityNotFoundException e) {
            logger.warn("Payment not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error getting payment by business ID: {}", paymentId, e);
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);
            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    /**
     * Update payment by business identifier
     */
    @Operation(
        summary = "Update payment by business ID",
        description = "Updates a payment entity by its business identifier with optional workflow transition"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment updated successfully",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "404", description = "Payment not found",
            content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PutMapping("/business/{paymentId}")
    public ResponseEntity<?> updatePaymentByBusinessId(
            @Parameter(description = "Business identifier of the payment", required = true)
            @PathVariable String paymentId,
            @Parameter(description = "Updated payment entity", required = true)
            @RequestBody Payment payment,
            @Parameter(description = "Optional workflow transition name")
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Payment> response = paymentInteractor.updatePaymentByBusinessId(paymentId, payment, transition);
            return ResponseEntity.ok(response);
        } catch (PaymentInteractor.EntityNotFoundException e) {
            logger.warn("Payment not found for update: {}", e.getMessage());
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error updating payment by business ID: {}", paymentId, e);
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);
            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    /**
     * Update payment with optional workflow transition
     */
    @Operation(
        summary = "Update payment by technical ID",
        description = "Updates a payment entity by its technical UUID with optional workflow transition"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment updated successfully",
            content = @Content(schema = @Schema(implementation = EntityWithMetadata.class))),
        @ApiResponse(responseCode = "400", description = "Invalid payment data or update failed")
    })
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePayment(
            @Parameter(description = "Technical UUID of the payment", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Updated payment entity", required = true)
            @RequestBody Payment payment,
            @Parameter(description = "Optional workflow transition name")
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Payment> response = paymentInteractor.updatePaymentById(id, payment, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating payment", e);
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);
            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    /**
     * Delete payment by technical UUID
     */
    @Operation(
        summary = "Delete payment",
        description = "Deletes a payment entity by its technical UUID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Payment deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Delete operation failed")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePayment(
        @Parameter(description = "Technical UUID of the payment", required = true)
        @PathVariable UUID id) {
        try {
            paymentInteractor.deletePayment(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting payment", e);
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);
            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    /**
     * Get all payments
     */
    @Operation(
        summary = "Get all payments",
        description = "Retrieves all payment entities. Use sparingly as this can be slow for large datasets."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payments retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Retrieval failed")
    })
    @GetMapping
    public ResponseEntity<?> getAllPayments() {
        try {
            List<EntityWithMetadata<Payment>> payments = paymentInteractor.getAllPayments();
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            logger.error("Error getting all payments", e);
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);
            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    /**
     * Search payments by loan
     */
    @Operation(
        summary = "Search payments by loan",
        description = "Retrieves all payments associated with a specific loan ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payments retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Search failed")
    })
    @GetMapping("/search/loan/{loanId}")
    public ResponseEntity<?> getPaymentsByLoan(
        @Parameter(description = "Loan ID to search for", required = true)
        @PathVariable String loanId) {
        try {
            List<EntityWithMetadata<Payment>> payments = paymentInteractor.getPaymentsByLoan(loanId);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            logger.error("Error searching payments by loan: {}", loanId, e);
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);
            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    /**
     * Advanced payment search
     */
    @Operation(
        summary = "Advanced payment search",
        description = "Performs advanced search on payments with multiple filter criteria including loan ID, amount range, and value date range"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search completed successfully"),
        @ApiResponse(responseCode = "400", description = "Search failed")
    })
    @PostMapping("/search/advanced")
    public ResponseEntity<?> advancedSearch(
            @Parameter(description = "Search criteria for payments", required = true)
            @RequestBody PaymentSearchRequest searchRequest) {
        try {
            PaymentInteractor.PaymentSearchCriteria criteria = new PaymentInteractor.PaymentSearchCriteria();
            criteria.setLoanId(searchRequest.getLoanId());
            criteria.setMinAmount(searchRequest.getMinAmount());
            criteria.setMaxAmount(searchRequest.getMaxAmount());
            criteria.setValueDateFrom(searchRequest.getValueDateFrom());
            criteria.setValueDateTo(searchRequest.getValueDateTo());

            List<EntityWithMetadata<Payment>> payments = paymentInteractor.advancedSearch(criteria);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            logger.error("Error performing advanced payment search", e);
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);
            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    /**
     * DTO for advanced search requests
     */
    @Getter
    @Setter
    @Schema(description = "Search criteria for advanced payment search")
    public static class PaymentSearchRequest {
        @Schema(description = "Loan ID to filter by", example = "LOAN-001")
        private String loanId;

        @Schema(description = "Minimum payment amount", example = "100.00")
        private BigDecimal minAmount;

        @Schema(description = "Maximum payment amount", example = "10000.00")
        private BigDecimal maxAmount;

        @Schema(description = "Value date from (inclusive)", example = "2024-01-01")
        private LocalDate valueDateFrom;

        @Schema(description = "Value date to (inclusive)", example = "2024-12-31")
        private LocalDate valueDateTo;
    }
}

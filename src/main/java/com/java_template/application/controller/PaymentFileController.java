package com.java_template.application.controller;

import com.java_template.application.entity.payment_file.version_1.PaymentFile;
import com.java_template.application.interactor.PaymentFileInteractor;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.util.CyodaExceptionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for payment file management. Delegates all business logic to PaymentFileInteractor.
 */
@RestController
@RequestMapping("/api/v1/payment-file")
@CrossOrigin(origins = "*")
@Tag(name = "Payment File Management", description = "APIs for managing payment file imports")
public class PaymentFileController {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentFileController.class);
    private final PaymentFileInteractor paymentFileInteractor;

    public PaymentFileController(PaymentFileInteractor paymentFileInteractor) {
        this.paymentFileInteractor = paymentFileInteractor;
    }

    @Operation(summary = "Create a new payment file", description = "Creates a new payment file entity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Payment file created successfully"),
        @ApiResponse(responseCode = "409", description = "Payment file with the same ID already exists"),
        @ApiResponse(responseCode = "400", description = "Invalid data or creation failed")
    })
    @PostMapping
    public ResponseEntity<?> createPaymentFile(
        @Parameter(description = "Payment file entity to create", required = true)
        @RequestBody PaymentFile paymentFile) {
        try {
            EntityWithMetadata<PaymentFile> response = paymentFileInteractor.createPaymentFile(paymentFile);
            return ResponseEntity.status(201).body(response);
        } catch (PaymentFileInteractor.DuplicateEntityException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating payment file", e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    @Operation(summary = "Get payment file by technical ID", description = "Retrieves a payment file by its technical UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment file found"),
        @ApiResponse(responseCode = "404", description = "Payment file not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<PaymentFile>> getPaymentFileById(
        @Parameter(description = "Technical UUID of the payment file", required = true)
        @PathVariable UUID id) {
        try {
            EntityWithMetadata<PaymentFile> response = paymentFileInteractor.getPaymentFileById(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting payment file by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Get payment file by business ID", description = "Retrieves a payment file by its business identifier")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment file found"),
        @ApiResponse(responseCode = "404", description = "Payment file not found")
    })
    @GetMapping("/business/{paymentFileId}")
    public ResponseEntity<?> getPaymentFileByBusinessId(
        @Parameter(description = "Business identifier of the payment file", required = true)
        @PathVariable String paymentFileId) {
        try {
            EntityWithMetadata<PaymentFile> response = paymentFileInteractor.getPaymentFileByBusinessId(paymentFileId);
            return ResponseEntity.ok(response);
        } catch (PaymentFileInteractor.EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error getting payment file by business ID: {}", paymentFileId, e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    @Operation(summary = "Update payment file by business ID", description = "Updates a payment file by its business identifier")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment file updated successfully"),
        @ApiResponse(responseCode = "404", description = "Payment file not found")
    })
    @PutMapping("/business/{paymentFileId}")
    public ResponseEntity<?> updatePaymentFileByBusinessId(
            @Parameter(description = "Business identifier", required = true) @PathVariable String paymentFileId,
            @Parameter(description = "Updated payment file", required = true) @RequestBody PaymentFile paymentFile,
            @Parameter(description = "Optional workflow transition") @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<PaymentFile> response = paymentFileInteractor.updatePaymentFileByBusinessId(paymentFileId, paymentFile, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating payment file by business ID: {}", paymentFileId, e);
            return ResponseEntity.status(404).body("PaymentFile with paymentFileId '" + paymentFileId + "' not found");
        }
    }

    @Operation(summary = "Update payment file by technical ID", description = "Updates a payment file by its technical UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment file updated successfully"),
        @ApiResponse(responseCode = "400", description = "Update failed")
    })
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePaymentFile(
            @Parameter(description = "Technical UUID", required = true) @PathVariable UUID id,
            @Parameter(description = "Updated payment file", required = true) @RequestBody PaymentFile paymentFile,
            @Parameter(description = "Optional workflow transition") @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<PaymentFile> response = paymentFileInteractor.updatePaymentFileById(id, paymentFile, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating payment file", e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    @Operation(summary = "Get all payment files", description = "Retrieves all payment file entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment files retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Retrieval failed")
    })
    @GetMapping
    public ResponseEntity<?> getAllPaymentFiles() {
        try {
            List<EntityWithMetadata<PaymentFile>> paymentFiles = paymentFileInteractor.getAllPaymentFiles();
            return ResponseEntity.ok(paymentFiles);
        } catch (Exception e) {
            logger.error("Error getting all payment files", e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }
}


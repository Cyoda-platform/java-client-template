package com.java_template.application.controller;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.interactor.AccrualInteractor;
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
 * ABOUTME: REST controller for accrual management. Delegates all business logic to AccrualInteractor.
 */
@RestController
@RequestMapping("/api/v1/accrual")
@CrossOrigin(origins = "*")
@Tag(name = "Accrual Management", description = "APIs for managing interest accruals")
public class AccrualController {
    
    private static final Logger logger = LoggerFactory.getLogger(AccrualController.class);
    private final AccrualInteractor accrualInteractor;

    public AccrualController(AccrualInteractor accrualInteractor) {
        this.accrualInteractor = accrualInteractor;
    }

    @Operation(summary = "Create a new accrual", description = "Creates a new accrual entity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Accrual created successfully"),
        @ApiResponse(responseCode = "409", description = "Accrual with the same ID already exists"),
        @ApiResponse(responseCode = "400", description = "Invalid data or creation failed")
    })
    @PostMapping
    public ResponseEntity<?> createAccrual(
        @Parameter(description = "Accrual entity to create", required = true)
        @RequestBody Accrual accrual) {
        try {
            EntityWithMetadata<Accrual> response = accrualInteractor.createAccrual(accrual);
            return ResponseEntity.status(201).body(response);
        } catch (AccrualInteractor.DuplicateEntityException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating accrual", e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    @Operation(summary = "Get accrual by technical ID")
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Accrual>> getAccrualById(
        @Parameter(description = "Technical UUID", required = true) @PathVariable UUID id) {
        try {
            EntityWithMetadata<Accrual> response = accrualInteractor.getAccrualById(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting accrual by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Get accrual by business ID")
    @GetMapping("/business/{accrualId}")
    public ResponseEntity<?> getAccrualByBusinessId(
        @Parameter(description = "Business identifier", required = true) @PathVariable String accrualId) {
        try {
            EntityWithMetadata<Accrual> response = accrualInteractor.getAccrualByBusinessId(accrualId);
            return ResponseEntity.ok(response);
        } catch (AccrualInteractor.EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error getting accrual by business ID: {}", accrualId, e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    @Operation(summary = "Update accrual by business ID")
    @PutMapping("/business/{accrualId}")
    public ResponseEntity<?> updateAccrualByBusinessId(
            @Parameter(description = "Business identifier", required = true) @PathVariable String accrualId,
            @Parameter(description = "Updated accrual", required = true) @RequestBody Accrual accrual,
            @Parameter(description = "Optional workflow transition") @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Accrual> response = accrualInteractor.updateAccrualByBusinessId(accrualId, accrual, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating accrual by business ID: {}", accrualId, e);
            return ResponseEntity.status(404).body("Accrual with accrualId '" + accrualId + "' not found");
        }
    }

    @Operation(summary = "Update accrual by technical ID")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateAccrual(
            @Parameter(description = "Technical UUID", required = true) @PathVariable UUID id,
            @Parameter(description = "Updated accrual", required = true) @RequestBody Accrual accrual,
            @Parameter(description = "Optional workflow transition") @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Accrual> response = accrualInteractor.updateAccrualById(id, accrual, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating accrual", e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    @Operation(summary = "Get all accruals", description = "Retrieves all accrual entities")
    @GetMapping
    public ResponseEntity<?> getAllAccruals() {
        try {
            List<EntityWithMetadata<Accrual>> accruals = accrualInteractor.getAllAccruals();
            return ResponseEntity.ok(accruals);
        } catch (Exception e) {
            logger.error("Error getting all accruals", e);
            
            String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);

            return ResponseEntity.badRequest().body(errorMessage);
        }
    }
}


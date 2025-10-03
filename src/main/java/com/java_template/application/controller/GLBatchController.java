package com.java_template.application.controller;

import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.application.interactor.GLBatchInteractor;
import com.java_template.common.dto.EntityWithMetadata;
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
 * ABOUTME: REST controller for GL batch management. Delegates all business logic to GLBatchInteractor.
 */
@RestController
@RequestMapping("/api/v1/gl-batch")
@CrossOrigin(origins = "*")
@Tag(name = "GL Batch Management", description = "APIs for managing general ledger batches")
public class GLBatchController {
    
    private static final Logger logger = LoggerFactory.getLogger(GLBatchController.class);
    private final GLBatchInteractor glBatchInteractor;

    public GLBatchController(GLBatchInteractor glBatchInteractor) {
        this.glBatchInteractor = glBatchInteractor;
    }

    @Operation(summary = "Create a new GL batch", description = "Creates a new general ledger batch entity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "GL batch created successfully"),
        @ApiResponse(responseCode = "409", description = "GL batch with the same ID already exists"),
        @ApiResponse(responseCode = "400", description = "Invalid data or creation failed")
    })
    @PostMapping
    public ResponseEntity<?> createGLBatch(
        @Parameter(description = "GL batch entity to create", required = true)
        @RequestBody GLBatch glBatch) {
        try {
            EntityWithMetadata<GLBatch> response = glBatchInteractor.createGLBatch(glBatch);
            return ResponseEntity.status(201).body(response);
        } catch (GLBatchInteractor.DuplicateEntityException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating GL batch", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get GL batch by technical ID")
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<GLBatch>> getGLBatchById(
        @Parameter(description = "Technical UUID", required = true) @PathVariable UUID id) {
        try {
            EntityWithMetadata<GLBatch> response = glBatchInteractor.getGLBatchById(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting GL batch by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Get GL batch by business ID")
    @GetMapping("/business/{glBatchId}")
    public ResponseEntity<EntityWithMetadata<GLBatch>> getGLBatchByBusinessId(
        @Parameter(description = "Business identifier", required = true) @PathVariable String glBatchId) {
        try {
            EntityWithMetadata<GLBatch> response = glBatchInteractor.getGLBatchByBusinessId(glBatchId);
            return ResponseEntity.ok(response);
        } catch (GLBatchInteractor.EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error getting GL batch by business ID: {}", glBatchId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Update GL batch by business ID")
    @PutMapping("/business/{glBatchId}")
    public ResponseEntity<?> updateGLBatchByBusinessId(
            @Parameter(description = "Business identifier", required = true) @PathVariable String glBatchId,
            @Parameter(description = "Updated GL batch", required = true) @RequestBody GLBatch glBatch,
            @Parameter(description = "Optional workflow transition") @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<GLBatch> response = glBatchInteractor.updateGLBatchByBusinessId(glBatchId, glBatch, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating GL batch by business ID: {}", glBatchId, e);
            return ResponseEntity.status(404).body("GLBatch with glBatchId '" + glBatchId + "' not found");
        }
    }

    @Operation(summary = "Update GL batch by technical ID")
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<GLBatch>> updateGLBatch(
            @Parameter(description = "Technical UUID", required = true) @PathVariable UUID id,
            @Parameter(description = "Updated GL batch", required = true) @RequestBody GLBatch glBatch,
            @Parameter(description = "Optional workflow transition") @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<GLBatch> response = glBatchInteractor.updateGLBatchById(id, glBatch, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating GL batch", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get all GL batches", description = "Retrieves all general ledger batch entities")
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<GLBatch>>> getAllGLBatches() {
        try {
            List<EntityWithMetadata<GLBatch>> batches = glBatchInteractor.getAllGLBatches();
            return ResponseEntity.ok(batches);
        } catch (Exception e) {
            logger.error("Error getting all GL batches", e);
            return ResponseEntity.badRequest().build();
        }
    }
}


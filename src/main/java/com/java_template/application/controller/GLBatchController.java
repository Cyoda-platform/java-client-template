package com.java_template.application.controller;

import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * ABOUTME: REST controller for GLBatch entity operations, providing endpoints
 * for managing month-end GL batch processing and export operations.
 */
@RestController
@RequestMapping("/ui/gl-batches")
@CrossOrigin(origins = "*")
public class GLBatchController {

    private static final Logger logger = LoggerFactory.getLogger(GLBatchController.class);
    private final EntityService entityService;

    public GLBatchController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create a new GL batch
     * POST /ui/gl-batches
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<GLBatch>> createGLBatch(@RequestBody GLBatch glBatch) {
        try {
            EntityWithMetadata<GLBatch> response = entityService.create(glBatch);
            logger.info("GLBatch created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create GL batch: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get GL batch by technical UUID
     * GET /ui/gl-batches/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<GLBatch>> getGLBatchById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
            EntityWithMetadata<GLBatch> response = entityService.getById(id, modelSpec, GLBatch.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * List all GL batches with pagination
     * GET /ui/gl-batches?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<?> listGLBatches(Pageable pageable) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
            return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, GLBatch.class));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list GL batches: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Prepare GL batch for period
     * POST /ui/gl-batches/{id}/prepare
     */
    @PostMapping("/{id}/prepare")
    public ResponseEntity<EntityWithMetadata<GLBatch>> prepareGLBatch(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
            EntityWithMetadata<GLBatch> current = entityService.getById(id, modelSpec, GLBatch.class);

            EntityWithMetadata<GLBatch> response = entityService.update(id, current.entity(), "prepare_batch");
            logger.info("GLBatch prepared with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to prepare GL batch with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Export GL batch
     * POST /ui/gl-batches/{id}/export
     */
    @PostMapping("/{id}/export")
    public ResponseEntity<EntityWithMetadata<GLBatch>> exportGLBatch(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
            EntityWithMetadata<GLBatch> current = entityService.getById(id, modelSpec, GLBatch.class);

            EntityWithMetadata<GLBatch> response = entityService.update(id, current.entity(), "export_batch");
            logger.info("GLBatch exported with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to export GL batch with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

package com.java_template.application.controller;

import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating GLBatch", e);
            return ResponseEntity.badRequest().build();
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
            logger.error("Error getting GLBatch by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * List all GL batches
     * GET /ui/gl-batches
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<GLBatch>>> listGLBatches() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
            List<EntityWithMetadata<GLBatch>> batches = entityService.findAll(modelSpec, GLBatch.class);
            return ResponseEntity.ok(batches);
        } catch (Exception e) {
            logger.error("Error listing GL batches", e);
            return ResponseEntity.badRequest().build();
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
            logger.error("Error preparing GLBatch: {}", id, e);
            return ResponseEntity.badRequest().build();
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
            logger.error("Error exporting GLBatch: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}

package com.java_template.application.interactor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Interactor for GL batch business logic. Handles all general ledger batch-related operations
 * including CRUD and batch management functionality.
 */
@Component
public class GLBatchInteractor {

    private static final Logger logger = LoggerFactory.getLogger(GLBatchInteractor.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public GLBatchInteractor(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    public EntityWithMetadata<GLBatch> createGLBatch(GLBatch glBatch) {
        // Validate business key is mandatory
        if (glBatch.getGlBatchId() == null || glBatch.getGlBatchId().trim().isEmpty()) {
            logger.error("GLBatch creation failed: glBatchId is mandatory");
            throw new IllegalArgumentException("glBatchId is mandatory and cannot be null or empty");
        }

        // Check for duplicate business key
        ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
        EntityWithMetadata<GLBatch> existing = entityService.findByBusinessId(
                modelSpec, glBatch.getGlBatchId(), "glBatchId", GLBatch.class);

        if (existing != null) {
            logger.warn("GLBatch with glBatchId {} already exists", glBatch.getGlBatchId());
            throw new DuplicateEntityException("GLBatch with glBatchId '" + glBatch.getGlBatchId() + "' already exists");
        }

        glBatch.setCreatedAt(LocalDateTime.now());
        glBatch.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<GLBatch> response = entityService.create(glBatch);
        logger.info("GLBatch created with ID: {}", response.metadata().getId());
        return response;
    }

    public EntityWithMetadata<GLBatch> getGLBatchById(UUID id) {
        ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
        EntityWithMetadata<GLBatch> response = entityService.getById(id, modelSpec, GLBatch.class);
        logger.debug("Retrieved GL batch by ID: {}", id);
        return response;
    }

    public EntityWithMetadata<GLBatch> getGLBatchByBusinessId(String glBatchId) {
        ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
        EntityWithMetadata<GLBatch> response = entityService.findByBusinessId(
                modelSpec, glBatchId, "glBatchId", GLBatch.class);

        if (response == null) {
            logger.warn("GLBatch not found with business ID: {}", glBatchId);
            throw new EntityNotFoundException("GLBatch not found with glBatchId: " + glBatchId);
        }
        
        logger.debug("Retrieved GL batch by business ID: {}", glBatchId);
        return response;
    }

    public EntityWithMetadata<GLBatch> updateGLBatchById(UUID id, GLBatch glBatch, String transition) {
        glBatch.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<GLBatch> response = entityService.update(id, glBatch, transition);
        logger.info("GLBatch updated with ID: {}", id);
        return response;
    }

    public EntityWithMetadata<GLBatch> updateGLBatchByBusinessId(String glBatchId, GLBatch glBatch, String transition) {
        glBatch.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<GLBatch> response = entityService.updateByBusinessId(glBatch, "glBatchId", transition);
        logger.info("GLBatch updated with business ID: {}", glBatchId);
        return response;
    }

    public List<EntityWithMetadata<GLBatch>> getAllGLBatches() {
        ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
        List<EntityWithMetadata<GLBatch>> batches = entityService.findAll(modelSpec, GLBatch.class);
        logger.debug("Retrieved {} GL batches", batches.size());
        return batches;
    }

    /**
     * Exception thrown when attempting to create a duplicate entity
     */
    public static class DuplicateEntityException extends RuntimeException {
        public DuplicateEntityException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when an entity is not found
     */
    public static class EntityNotFoundException extends RuntimeException {
        public EntityNotFoundException(String message) {
            super(message);
        }
    }
}


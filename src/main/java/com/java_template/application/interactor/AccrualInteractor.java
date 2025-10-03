package com.java_template.application.interactor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.accrual.version_1.Accrual;
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
 * ABOUTME: Interactor for accrual business logic. Handles all accrual-related operations
 * including CRUD and interest accrual management functionality.
 */
@Component
public class AccrualInteractor {

    private static final Logger logger = LoggerFactory.getLogger(AccrualInteractor.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AccrualInteractor(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    public EntityWithMetadata<Accrual> createAccrual(Accrual accrual) {
        // Validate business key is mandatory
        if (accrual.getAccrualId() == null || accrual.getAccrualId().trim().isEmpty()) {
            logger.error("Accrual creation failed: accrualId is mandatory");
            throw new IllegalArgumentException("accrualId is mandatory and cannot be null or empty");
        }

        // Check for duplicate business key
        ModelSpec modelSpec = new ModelSpec().withName(Accrual.ENTITY_NAME).withVersion(Accrual.ENTITY_VERSION);
        EntityWithMetadata<Accrual> existing = entityService.findByBusinessId(
                modelSpec, accrual.getAccrualId(), "accrualId", Accrual.class);

        if (existing != null) {
            logger.warn("Accrual with accrualId {} already exists", accrual.getAccrualId());
            throw new DuplicateEntityException("Accrual with accrualId '" + accrual.getAccrualId() + "' already exists");
        }

        accrual.setScheduledAt(LocalDateTime.now());
        accrual.setCreatedAt(LocalDateTime.now());
        accrual.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<Accrual> response = entityService.create(accrual);
        logger.info("Accrual created with ID: {}", response.metadata().getId());
        return response;
    }

    public EntityWithMetadata<Accrual> getAccrualById(UUID id) {
        ModelSpec modelSpec = new ModelSpec().withName(Accrual.ENTITY_NAME).withVersion(Accrual.ENTITY_VERSION);
        EntityWithMetadata<Accrual> response = entityService.getById(id, modelSpec, Accrual.class);
        logger.debug("Retrieved accrual by ID: {}", id);
        return response;
    }

    public EntityWithMetadata<Accrual> getAccrualByBusinessId(String accrualId) {
        ModelSpec modelSpec = new ModelSpec().withName(Accrual.ENTITY_NAME).withVersion(Accrual.ENTITY_VERSION);
        EntityWithMetadata<Accrual> response = entityService.findByBusinessId(
                modelSpec, accrualId, "accrualId", Accrual.class);

        if (response == null) {
            logger.warn("Accrual not found with business ID: {}", accrualId);
            throw new EntityNotFoundException("Accrual not found with accrualId: " + accrualId);
        }
        
        logger.debug("Retrieved accrual by business ID: {}", accrualId);
        return response;
    }

    public EntityWithMetadata<Accrual> updateAccrualById(UUID id, Accrual accrual, String transition) {
        accrual.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<Accrual> response = entityService.update(id, accrual, transition);
        logger.info("Accrual updated with ID: {}", id);
        return response;
    }

    public EntityWithMetadata<Accrual> updateAccrualByBusinessId(String accrualId, Accrual accrual, String transition) {
        accrual.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<Accrual> response = entityService.updateByBusinessId(accrual, "accrualId", transition);
        logger.info("Accrual updated with business ID: {}", accrualId);
        return response;
    }

    public List<EntityWithMetadata<Accrual>> getAllAccruals() {
        ModelSpec modelSpec = new ModelSpec().withName(Accrual.ENTITY_NAME).withVersion(Accrual.ENTITY_VERSION);
        List<EntityWithMetadata<Accrual>> accruals = entityService.findAll(modelSpec, Accrual.class);
        logger.debug("Retrieved {} accruals", accruals.size());
        return accruals;
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


package com.java_template.application.interactor;

import com.java_template.application.entity.settlement_quote.version_1.SettlementQuote;
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
 * ABOUTME: Interactor for settlement quote business logic. Handles all settlement quote-related operations
 * including CRUD and loan settlement quote management functionality.
 */
@Component
public class SettlementQuoteInteractor {

    private static final Logger logger = LoggerFactory.getLogger(SettlementQuoteInteractor.class);
    private final EntityService entityService;

    public SettlementQuoteInteractor(EntityService entityService) {
        this.entityService = entityService;
    }

    public EntityWithMetadata<SettlementQuote> createSettlementQuote(SettlementQuote settlementQuote) {
        // Validate business key is not empty
        if (settlementQuote.getSettlementQuoteId().trim().isEmpty()) {
            logger.error("SettlementQuote creation failed: settlementQuoteId cannot be empty");
            throw new IllegalArgumentException("settlementQuoteId cannot be empty");
        }

        // Check for duplicate business key
        ModelSpec modelSpec = new ModelSpec().withName(SettlementQuote.ENTITY_NAME).withVersion(SettlementQuote.ENTITY_VERSION);
        EntityWithMetadata<SettlementQuote> existing = entityService.findByBusinessIdOrNull(
                modelSpec, settlementQuote.getSettlementQuoteId(), "settlementQuoteId", SettlementQuote.class);

        if (existing != null) {
            logger.warn("SettlementQuote with settlementQuoteId {} already exists", settlementQuote.getSettlementQuoteId());
            throw new DuplicateEntityException("SettlementQuote with settlementQuoteId '" + settlementQuote.getSettlementQuoteId() + "' already exists");
        }

        settlementQuote.setQuotedDate(settlementQuote.getAsOfDate());
        settlementQuote.setCreatedAt(LocalDateTime.now());
        settlementQuote.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<SettlementQuote> response = entityService.create(settlementQuote);
        logger.info("SettlementQuote created with ID: {}", response.metadata().getId());
        return response;
    }

    public EntityWithMetadata<SettlementQuote> getSettlementQuoteById(UUID id) {
        ModelSpec modelSpec = new ModelSpec().withName(SettlementQuote.ENTITY_NAME).withVersion(SettlementQuote.ENTITY_VERSION);
        EntityWithMetadata<SettlementQuote> response = entityService.getById(id, modelSpec, SettlementQuote.class);
        logger.debug("Retrieved settlement quote by ID: {}", id);
        return response;
    }

    public EntityWithMetadata<SettlementQuote> getSettlementQuoteByBusinessId(String settlementQuoteId) {
        ModelSpec modelSpec = new ModelSpec().withName(SettlementQuote.ENTITY_NAME).withVersion(SettlementQuote.ENTITY_VERSION);
        EntityWithMetadata<SettlementQuote> response = entityService.findByBusinessId(
                modelSpec, settlementQuoteId, "settlementQuoteId", SettlementQuote.class);

        if (response == null) {
            logger.warn("SettlementQuote not found with business ID: {}", settlementQuoteId);
            throw new EntityNotFoundException("SettlementQuote not found with settlementQuoteId: " + settlementQuoteId);
        }
        
        logger.debug("Retrieved settlement quote by business ID: {}", settlementQuoteId);
        return response;
    }

    public EntityWithMetadata<SettlementQuote> updateSettlementQuoteById(UUID id, SettlementQuote settlementQuote, String transition) {
        settlementQuote.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<SettlementQuote> response = entityService.update(id, settlementQuote, transition);
        logger.info("SettlementQuote updated with ID: {}", id);
        return response;
    }

    public EntityWithMetadata<SettlementQuote> updateSettlementQuoteByBusinessId(String settlementQuoteId, SettlementQuote settlementQuote, String transition) {
        settlementQuote.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<SettlementQuote> response = entityService.updateByBusinessId(settlementQuote, "settlementQuoteId", transition);
        logger.info("SettlementQuote updated with business ID: {}", settlementQuoteId);
        return response;
    }

    public List<EntityWithMetadata<SettlementQuote>> getAllSettlementQuotes() {
        ModelSpec modelSpec = new ModelSpec().withName(SettlementQuote.ENTITY_NAME).withVersion(SettlementQuote.ENTITY_VERSION);
        List<EntityWithMetadata<SettlementQuote>> quotes = entityService.findAll(modelSpec, SettlementQuote.class);
        logger.debug("Retrieved {} settlement quotes", quotes.size());
        return quotes;
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


package com.java_template.application.interactor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.party.version_1.Party;
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
 * ABOUTME: Interactor for party business logic. Handles all party-related operations
 * including CRUD and basic search functionality.
 */
@Component
public class PartyInteractor {

    private static final Logger logger = LoggerFactory.getLogger(PartyInteractor.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PartyInteractor(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    public EntityWithMetadata<Party> createParty(Party party) {
        // Validate business key is mandatory
        if (party.getPartyId() == null || party.getPartyId().trim().isEmpty()) {
            logger.error("Party creation failed: partyId is mandatory");
            throw new IllegalArgumentException("partyId is mandatory and cannot be null or empty");
        }

        // Check for duplicate business key
        ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
        EntityWithMetadata<Party> existing = entityService.findByBusinessId(
                modelSpec, party.getPartyId(), "partyId", Party.class);

        if (existing != null) {
            logger.warn("Party with partyId {} already exists", party.getPartyId());
            throw new DuplicateEntityException("Party with partyId '" + party.getPartyId() + "' already exists");
        }

        party.setCreatedAt(LocalDateTime.now());
        party.setUpdatedAt(LocalDateTime.now());
        party.setStatus("ACTIVE");

        EntityWithMetadata<Party> response = entityService.create(party);
        logger.info("Party created with ID: {}", response.metadata().getId());
        return response;
    }

    public EntityWithMetadata<Party> getPartyById(UUID id) {
        ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
        EntityWithMetadata<Party> response = entityService.getById(id, modelSpec, Party.class);
        logger.debug("Retrieved party by ID: {}", id);
        return response;
    }

    public EntityWithMetadata<Party> getPartyByBusinessId(String partyId) {
        ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
        EntityWithMetadata<Party> response = entityService.findByBusinessId(
                modelSpec, partyId, "partyId", Party.class);

        if (response == null) {
            logger.warn("Party not found with business ID: {}", partyId);
            throw new EntityNotFoundException("Party not found with partyId: " + partyId);
        }
        
        logger.debug("Retrieved party by business ID: {}", partyId);
        return response;
    }

    public EntityWithMetadata<Party> updatePartyById(UUID id, Party party, String transition) {
        party.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<Party> response = entityService.update(id, party, transition);
        logger.info("Party updated with ID: {}", id);
        return response;
    }

    public EntityWithMetadata<Party> updatePartyByBusinessId(String partyId, Party party, String transition) {
        party.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<Party> response = entityService.updateByBusinessId(party, "partyId", transition);
        logger.info("Party updated with business ID: {}", partyId);
        return response;
    }

    public void deleteParty(UUID id) {
        entityService.deleteById(id);
        logger.info("Party deleted with ID: {}", id);
    }

    public List<EntityWithMetadata<Party>> getAllParties() {
        ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
        List<EntityWithMetadata<Party>> parties = entityService.findAll(modelSpec, Party.class);
        logger.debug("Retrieved {} parties", parties.size());
        return parties;
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


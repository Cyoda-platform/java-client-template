package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.party.version_1.Party;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: This controller provides REST endpoints for party management including
 * CRUD operations and basic search functionality.
 */
@RestController
@RequestMapping("/ui/party")
@CrossOrigin(origins = "*")
public class PartyController {

    private static final Logger logger = LoggerFactory.getLogger(PartyController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PartyController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Party>> createParty(@RequestBody Party party) {
        try {
            party.setCreatedAt(LocalDateTime.now());
            party.setUpdatedAt(LocalDateTime.now());
            party.setStatus("ACTIVE");

            EntityWithMetadata<Party> response = entityService.create(party);
            logger.info("Party created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating party", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Party>> getPartyById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            EntityWithMetadata<Party> response = entityService.getById(id, modelSpec, Party.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting party by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/business/{partyId}")
    public ResponseEntity<EntityWithMetadata<Party>> getPartyByBusinessId(@PathVariable String partyId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            EntityWithMetadata<Party> response = entityService.findByBusinessId(
                    modelSpec, partyId, "partyId", Party.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting party by business ID: {}", partyId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Party>> updateParty(
            @PathVariable UUID id,
            @RequestBody Party party,
            @RequestParam(required = false) String transition) {
        try {
            party.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Party> response = entityService.update(id, party, transition);
            logger.info("Party updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating party", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteParty(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Party deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting party", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Party>>> getAllParties() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            List<EntityWithMetadata<Party>> parties = entityService.findAll(modelSpec, Party.class);
            return ResponseEntity.ok(parties);
        } catch (Exception e) {
            logger.error("Error getting all parties", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.party.version_1.Party;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Party entity operations, providing CRUD endpoints
 * for managing legal entities (borrowers, lenders, agents) in the loan system.
 */
@RestController
@RequestMapping("/ui/parties")
@CrossOrigin(origins = "*")
public class PartyController {

    private static final Logger logger = LoggerFactory.getLogger(PartyController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PartyController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new party
     * POST /ui/parties
     */
    @PostMapping
    public ResponseEntity<?> createParty(@RequestBody Party party) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            EntityWithMetadata<Party> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, party.getPartyId(), "partyId", Party.class);

            if (existing != null) {
                logger.warn("Party with business ID {} already exists", party.getPartyId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Party already exists with ID: %s", party.getPartyId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Set default status if not provided
            if (party.getStatus() == null || party.getStatus().trim().isEmpty()) {
                party.setStatus("ACTIVE");
            }

            EntityWithMetadata<Party> response = entityService.create(party);
            logger.info("Party created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Party", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get party by technical UUID
     * GET /ui/parties/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Party>> getPartyById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            EntityWithMetadata<Party> response = entityService.getById(id, modelSpec, Party.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Party by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get party by business identifier
     * GET /ui/parties/business/{partyId}
     */
    @GetMapping("/business/{partyId}")
    public ResponseEntity<EntityWithMetadata<Party>> getPartyByBusinessId(@PathVariable String partyId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            EntityWithMetadata<Party> response = entityService.findByBusinessIdOrNull(
                    modelSpec, partyId, "partyId", Party.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Party by business ID: {}", partyId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update party with optional workflow transition
     * PUT /ui/parties/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Party>> updateParty(
            @PathVariable UUID id,
            @RequestBody Party party,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Party> response = entityService.update(id, party, transition);
            logger.info("Party updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Party: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * List all parties with optional filtering
     * GET /ui/parties?status=ACTIVE&jurisdiction=GB
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Party>>> listParties(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String jurisdiction) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            
            List<QueryCondition> conditions = new ArrayList<>();
            
            if (status != null && !status.trim().isEmpty()) {
                SimpleCondition statusCondition = new SimpleCondition()
                        .withJsonPath("$.status")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(status));
                conditions.add(statusCondition);
            }
            
            if (jurisdiction != null && !jurisdiction.trim().isEmpty()) {
                SimpleCondition jurisdictionCondition = new SimpleCondition()
                        .withJsonPath("$.jurisdiction")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(jurisdiction));
                conditions.add(jurisdictionCondition);
            }

            List<EntityWithMetadata<Party>> parties;
            if (conditions.isEmpty()) {
                parties = entityService.findAll(modelSpec, Party.class);
            } else {
                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                parties = entityService.search(modelSpec, groupCondition, Party.class);
            }

            return ResponseEntity.ok(parties);
        } catch (Exception e) {
            logger.error("Error listing parties", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search parties by name
     * GET /ui/parties/search?name=searchTerm
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Party>>> searchPartiesByName(
            @RequestParam String name) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);

            SimpleCondition nameCondition = new SimpleCondition()
                    .withJsonPath("$.legalName")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(name));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(nameCondition));

            List<EntityWithMetadata<Party>> parties = entityService.search(modelSpec, condition, Party.class);
            return ResponseEntity.ok(parties);
        } catch (Exception e) {
            logger.error("Error searching parties by name: {}", name, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Deactivate party
     * POST /ui/parties/{id}/deactivate
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<EntityWithMetadata<Party>> deactivateParty(@PathVariable UUID id) {
        try {
            // Get current party
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            EntityWithMetadata<Party> current = entityService.getById(id, modelSpec, Party.class);
            
            Party party = current.entity();
            party.setStatus("INACTIVE");
            
            EntityWithMetadata<Party> response = entityService.update(id, party, "deactivate_party");
            logger.info("Party deactivated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deactivating Party: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reactivate party
     * POST /ui/parties/{id}/reactivate
     */
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<EntityWithMetadata<Party>> reactivateParty(@PathVariable UUID id) {
        try {
            // Get current party
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            EntityWithMetadata<Party> current = entityService.getById(id, modelSpec, Party.class);
            
            Party party = current.entity();
            party.setStatus("ACTIVE");
            
            EntityWithMetadata<Party> response = entityService.update(id, party, "reactivate_party");
            logger.info("Party reactivated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error reactivating Party: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}

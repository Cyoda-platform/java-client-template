package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.party.version_1.Party;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.CyodaExceptionUtil;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
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
    public ResponseEntity<EntityWithMetadata<Party>> createParty(@RequestBody Party party) {
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

            EntityWithMetadata<Party> response = entityService.create(party);
            logger.info("Party created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create party: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get party by technical UUID
     * GET /ui/parties/{id}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Party>> getPartyById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Party> response = entityService.getById(id, modelSpec, Party.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve loan with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get party by business identifier
     * GET /ui/parties/business/{partyId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/business/{partyId}")
    public ResponseEntity<EntityWithMetadata<Party>> getPartyByBusinessId(
            @PathVariable String partyId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Party> response = entityService.findByBusinessId(
                    modelSpec, partyId, "partyId", Party.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve party with business ID '%s': %s", partyId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get party change history metadata
     * GET /ui/parties/{id}/changes?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<List<EntityChangeMeta>> getPartyChangesMetadata(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            List<org.cyoda.cloud.api.event.common.EntityChangeMeta> changes =
                    entityService.getEntityChangesMetadata(id, pointInTimeDate);
            return ResponseEntity.ok(changes);
        } catch (Exception e) {
            // Check if it's a NOT_FOUND error (entity doesn't exist)
            if (CyodaExceptionUtil.isNotFound(e)) {
                return ResponseEntity.notFound().build();
            }
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve change history for party with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update party with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * List all parties with pagination and optional filtering
     * GET /ui/parties?page=0&size=20&status=ACTIVE&jurisdiction=GB&pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Party>>> listParties(
            Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String jurisdiction,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

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

            if (conditions.isEmpty()) {
                // Use paginated findAll when no filters
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, Party.class, pointInTimeDate));
            } else {
                // For filtered results, get all matching results then manually paginate
                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                List<EntityWithMetadata<Party>> parties = entityService.search(modelSpec, groupCondition, Party.class, pointInTimeDate);

                // Manually paginate the filtered results
                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), parties.size());
                List<EntityWithMetadata<Party>> pageContent = start < parties.size()
                    ? parties.subList(start, end)
                    : new ArrayList<>();

                Page<EntityWithMetadata<Party>> page = new PageImpl<>(pageContent, pageable, parties.size());
                return ResponseEntity.ok(page);
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list parties: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Search parties by name
     * GET /ui/parties/search?name=searchTerm&pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Party>>> searchPartiesByName(
            @RequestParam String name,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            SimpleCondition nameCondition = new SimpleCondition()
                    .withJsonPath("$.legalName")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(name));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(nameCondition));

            List<EntityWithMetadata<Party>> parties = entityService.search(modelSpec, condition, Party.class, pointInTimeDate);
            return ResponseEntity.ok(parties);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search parties by name '%s': %s", name, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Deactivate party
     * POST /ui/parties/{id}/deactivate
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<EntityWithMetadata<Party>> deactivateParty(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            EntityWithMetadata<Party> current = entityService.getById(id, modelSpec, Party.class);

            EntityWithMetadata<Party> response = entityService.update(id, current.entity(), "deactivate_party");
            logger.info("Party deactivated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to deactivate party with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Reactivate party
     * POST /ui/parties/{id}/reactivate
     */
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<EntityWithMetadata<Party>> reactivateParty(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            EntityWithMetadata<Party> current = entityService.getById(id, modelSpec, Party.class);

            EntityWithMetadata<Party> response = entityService.update(id, current.entity(), "reactivate_party");
            logger.info("Party reactivated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to reactivate party with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete party by technical UUID
     * DELETE /ui/parties/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteParty(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Party deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete party with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete party by business identifier
     * DELETE /ui/parties/business/{partyId}
     */
    @DeleteMapping("/business/{partyId}")
    public ResponseEntity<Void> deletePartyByBusinessId(@PathVariable String partyId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            boolean deleted = entityService.deleteByBusinessId(modelSpec, partyId, "partyId", Party.class);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Party deleted with business ID: {}", partyId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete party with business ID '%s': %s", partyId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete all parties (DANGEROUS - use with caution)
     * DELETE /ui/parties
     */
    @DeleteMapping
    public ResponseEntity<String> deleteAllParties() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
            Integer deletedCount = entityService.deleteAll(modelSpec);
            logger.warn("Deleted all Parties - count: {}", deletedCount);
            return ResponseEntity.ok().body(String.format("Deleted %d parties", deletedCount));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete all parties: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

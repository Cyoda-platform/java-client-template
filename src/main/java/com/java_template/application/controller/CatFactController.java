package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CatFactController - REST API controller for cat fact management
 * 
 * Base Path: /api/catfacts
 * 
 * Provides endpoints for:
 * - Cat fact ingestion from API
 * - Cat fact validation
 * - Cat fact usage marking
 * - Cat fact archiving
 * - Cat fact listing and search
 */
@RestController
@RequestMapping("/api/catfacts")
@CrossOrigin(origins = "*")
public class CatFactController {

    private static final Logger logger = LoggerFactory.getLogger(CatFactController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CatFactController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Trigger cat fact ingestion from API
     * POST /api/catfacts/ingest
     */
    @PostMapping("/ingest")
    public ResponseEntity<EntityWithMetadata<CatFact>> ingestCatFact(@RequestBody CatFactIngestionRequest request) {
        try {
            CatFact catFact = new CatFact();
            // The processor will populate the actual data from the API
            catFact.setFactId("temp_" + UUID.randomUUID().toString().substring(0, 8));
            catFact.setText("Temporary fact text");
            catFact.setLength(18);
            catFact.setRetrievedDate(LocalDateTime.now());
            catFact.setSource(request.getSource() != null ? request.getSource() : "catfact.ninja");
            catFact.setIsUsed(false);
            catFact.setUsageCount(0);

            EntityWithMetadata<CatFact> response = entityService.create(catFact);
            logger.info("CatFact ingestion triggered with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error triggering cat fact ingestion", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Validate retrieved cat fact
     * PUT /api/catfacts/{uuid}/validate
     */
    @PutMapping("/{id}/validate")
    public ResponseEntity<EntityWithMetadata<CatFact>> validateCatFact(
            @PathVariable UUID id,
            @RequestBody CatFactTransitionRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> current = entityService.getById(id, modelSpec, CatFact.class);
            
            EntityWithMetadata<CatFact> response = entityService.update(id, current.entity(), "transition_to_ready");
            logger.info("CatFact validated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error validating cat fact", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mark cat fact as used in campaign
     * PUT /api/catfacts/{uuid}/use
     */
    @PutMapping("/{id}/use")
    public ResponseEntity<EntityWithMetadata<CatFact>> useCatFact(
            @PathVariable UUID id,
            @RequestBody CatFactTransitionRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> current = entityService.getById(id, modelSpec, CatFact.class);
            
            EntityWithMetadata<CatFact> response = entityService.update(id, current.entity(), "transition_to_used");
            logger.info("CatFact marked as used with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error marking cat fact as used", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Archive overused cat fact
     * PUT /api/catfacts/{uuid}/archive
     */
    @PutMapping("/{id}/archive")
    public ResponseEntity<EntityWithMetadata<CatFact>> archiveCatFact(
            @PathVariable UUID id,
            @RequestBody CatFactTransitionRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> current = entityService.getById(id, modelSpec, CatFact.class);
            
            EntityWithMetadata<CatFact> response = entityService.update(id, current.entity(), "transition_to_archived");
            logger.info("CatFact archived with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error archiving cat fact", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get cat fact by UUID
     * GET /api/catfacts/{uuid}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<CatFact>> getCatFactById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> response = entityService.getById(id, modelSpec, CatFact.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting cat fact by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * List cat facts with filtering
     * GET /api/catfacts
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<CatFact>>> getAllCatFacts(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Boolean unused) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            
            if (state != null || unused != null) {
                List<QueryCondition> conditions = new java.util.ArrayList<>();

                if (state != null && !state.trim().isEmpty()) {
                    SimpleCondition stateCondition = new SimpleCondition()
                            .withJsonPath("$.meta.state")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(state));
                    conditions.add(stateCondition);
                }

                if (unused != null && unused) {
                    SimpleCondition unusedCondition = new SimpleCondition()
                            .withJsonPath("$.isUsed")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(false));
                    conditions.add(unusedCondition);
                }

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);

                List<EntityWithMetadata<CatFact>> catFacts = entityService.search(modelSpec, condition, CatFact.class);
                return ResponseEntity.ok(catFacts);
            } else {
                List<EntityWithMetadata<CatFact>> catFacts = entityService.findAll(modelSpec, CatFact.class);
                return ResponseEntity.ok(catFacts);
            }
        } catch (Exception e) {
            logger.error("Error getting cat facts", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get random ready cat fact for campaign use
     * GET /api/catfacts/random
     */
    @GetMapping("/random")
    public ResponseEntity<EntityWithMetadata<CatFact>> getRandomReadyCatFact() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            
            // Search for ready cat facts
            SimpleCondition readyCondition = new SimpleCondition()
                    .withJsonPath("$.meta.state")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree("ready"));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(readyCondition));

            List<EntityWithMetadata<CatFact>> readyCatFacts = entityService.search(modelSpec, condition, CatFact.class);
            
            if (readyCatFacts.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            // Return a random ready cat fact
            int randomIndex = (int) (Math.random() * readyCatFacts.size());
            return ResponseEntity.ok(readyCatFacts.get(randomIndex));
        } catch (Exception e) {
            logger.error("Error getting random ready cat fact", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    @Getter
    @Setter
    public static class CatFactIngestionRequest {
        private String source;
        private Integer count;
    }

    @Getter
    @Setter
    public static class CatFactTransitionRequest {
        private String transitionName;
        private String campaignId;
        private String reason;
    }
}

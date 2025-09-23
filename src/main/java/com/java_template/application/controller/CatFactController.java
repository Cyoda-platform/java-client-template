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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CatFactController - REST controller for cat fact management
 * 
 * Provides endpoints for managing cat facts including retrieval,
 * content management, and usage tracking.
 */
@RestController
@RequestMapping("/ui/catfact")
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
     * Create a new cat fact
     * POST /ui/catfact
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<CatFact>> createCatFact(@RequestBody CatFact catFact) {
        try {
            // Set creation timestamp
            catFact.setCreatedAt(LocalDateTime.now());
            catFact.setUpdatedAt(LocalDateTime.now());
            
            // Set retrieved date if not provided
            if (catFact.getRetrievedDate() == null) {
                catFact.setRetrievedDate(LocalDateTime.now());
            }
            
            // Set used status if not provided
            if (catFact.getIsUsed() == null) {
                catFact.setIsUsed(false);
            }

            EntityWithMetadata<CatFact> response = entityService.create(catFact);
            logger.info("CatFact created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating CatFact", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get cat fact by technical UUID
     * GET /ui/catfact/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<CatFact>> getCatFactById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> response = entityService.getById(id, modelSpec, CatFact.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting CatFact by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get cat fact by business identifier (factId)
     * GET /ui/catfact/business/{factId}
     */
    @GetMapping("/business/{factId}")
    public ResponseEntity<EntityWithMetadata<CatFact>> getCatFactByBusinessId(@PathVariable String factId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> response = entityService.findByBusinessId(
                    modelSpec, factId, "factId", CatFact.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting CatFact by business ID: {}", factId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update cat fact with optional workflow transition
     * PUT /ui/catfact/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<CatFact>> updateCatFact(
            @PathVariable UUID id,
            @RequestBody CatFact catFact,
            @RequestParam(required = false) String transition) {
        try {
            // Set update timestamp
            catFact.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<CatFact> response = entityService.update(id, catFact, transition);
            logger.info("CatFact updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating CatFact", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete cat fact by technical UUID
     * DELETE /ui/catfact/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCatFact(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("CatFact deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting CatFact", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all cat facts
     * GET /ui/catfact
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<CatFact>>> getAllCatFacts() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            List<EntityWithMetadata<CatFact>> catFacts = entityService.findAll(modelSpec, CatFact.class);
            return ResponseEntity.ok(catFacts);
        } catch (Exception e) {
            logger.error("Error getting all CatFacts", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get available (unused) cat facts
     * GET /ui/catfact/available
     */
    @GetMapping("/available")
    public ResponseEntity<List<EntityWithMetadata<CatFact>>> getAvailableCatFacts() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);

            SimpleCondition unusedCondition = new SimpleCondition()
                    .withJsonPath("$.isUsed")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(false));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of((QueryCondition) unusedCondition));

            List<EntityWithMetadata<CatFact>> catFacts = entityService.search(modelSpec, condition, CatFact.class);
            return ResponseEntity.ok(catFacts);
        } catch (Exception e) {
            logger.error("Error getting available CatFacts", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mark cat fact as used
     * POST /ui/catfact/{id}/mark-used
     */
    @PostMapping("/{id}/mark-used")
    public ResponseEntity<EntityWithMetadata<CatFact>> markCatFactAsUsed(@PathVariable UUID id) {
        try {
            // Get current cat fact
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> current = entityService.getById(id, modelSpec, CatFact.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            // Update to used
            CatFact catFact = current.entity();
            catFact.setIsUsed(true);
            catFact.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<CatFact> response = entityService.update(id, catFact, "mark_used");
            logger.info("CatFact marked as used with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error marking CatFact as used: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search cat facts by content
     * GET /ui/catfact/search?content=keyword
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<CatFact>>> searchCatFactsByContent(
            @RequestParam String content) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);

            SimpleCondition contentCondition = new SimpleCondition()
                    .withJsonPath("$.content")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(content));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of((QueryCondition) contentCondition));

            List<EntityWithMetadata<CatFact>> catFacts = entityService.search(modelSpec, condition, CatFact.class);
            return ResponseEntity.ok(catFacts);
        } catch (Exception e) {
            logger.error("Error searching CatFacts by content: {}", content, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get cat facts by source
     * GET /ui/catfact/source/{source}
     */
    @GetMapping("/source/{source}")
    public ResponseEntity<List<EntityWithMetadata<CatFact>>> getCatFactsBySource(@PathVariable String source) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);

            SimpleCondition sourceCondition = new SimpleCondition()
                    .withJsonPath("$.source")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(source));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of((QueryCondition) sourceCondition));

            List<EntityWithMetadata<CatFact>> catFacts = entityService.search(modelSpec, condition, CatFact.class);
            return ResponseEntity.ok(catFacts);
        } catch (Exception e) {
            logger.error("Error getting CatFacts by source: {}", source, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for cat facts
     * POST /ui/catfact/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<CatFact>>> advancedSearch(
            @RequestBody CatFactSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getContent() != null && !searchRequest.getContent().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.content")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getContent())));
            }

            if (searchRequest.getSource() != null && !searchRequest.getSource().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.source")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getSource())));
            }

            if (searchRequest.getIsUsed() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.isUsed")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getIsUsed())));
            }

            if (searchRequest.getCategory() != null && !searchRequest.getCategory().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.category")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getCategory())));
            }

            if (searchRequest.getMinQualityScore() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.metadata.qualityScore")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinQualityScore())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<CatFact>> catFacts = entityService.search(modelSpec, condition, CatFact.class);
            return ResponseEntity.ok(catFacts);
        } catch (Exception e) {
            logger.error("Error performing advanced search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced search requests
     */
    @Getter
    @Setter
    public static class CatFactSearchRequest {
        private String content;
        private String source;
        private Boolean isUsed;
        private String category;
        private Double minQualityScore;
    }
}

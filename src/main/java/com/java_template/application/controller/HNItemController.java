package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
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
 * HNItemController - REST API endpoints for managing Hacker News items
 * 
 * Provides CRUD operations, search functionality, and Firebase API integration
 * for HNItem entities including hierarchical queries and bulk operations.
 */
@RestController
@RequestMapping("/api/hnitem")
@CrossOrigin(origins = "*")
public class HNItemController {

    private static final Logger logger = LoggerFactory.getLogger(HNItemController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public HNItemController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a single HN item
     * POST /api/hnitem
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<HNItem>> createEntity(@RequestBody HNItem entity) {
        try {
            // Set creation timestamp and source type
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            if (entity.getSourceType() == null) {
                entity.setSourceType("SINGLE_POST");
            }

            EntityWithMetadata<HNItem> response = entityService.create(entity);
            logger.info("HNItem created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating HNItem", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create multiple HN items from an array
     * POST /api/hnitem/batch
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchCreateResponse> createBatch(@RequestBody List<HNItem> entities) {
        try {
            List<EntityWithMetadata<HNItem>> successful = new ArrayList<>();
            List<FailedItem> failed = new ArrayList<>();

            for (HNItem entity : entities) {
                try {
                    // Set creation timestamp and source type
                    entity.setCreatedAt(LocalDateTime.now());
                    entity.setUpdatedAt(LocalDateTime.now());
                    if (entity.getSourceType() == null) {
                        entity.setSourceType("ARRAY_POST");
                    }

                    EntityWithMetadata<HNItem> response = entityService.create(entity);
                    successful.add(response);
                } catch (Exception e) {
                    failed.add(new FailedItem(entity, e.getMessage()));
                    logger.warn("Failed to create HNItem {}: {}", entity.getId(), e.getMessage());
                }
            }

            BatchCreateResponse response = new BatchCreateResponse();
            response.setSuccessful(successful);
            response.setFailed(failed);
            response.setSummary(new BatchSummary(entities.size(), successful.size(), failed.size()));

            logger.info("Batch create completed: {} successful, {} failed", successful.size(), failed.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in batch create", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get HN item by technical UUID
     * GET /api/hnitem/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<HNItem>> getEntityById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            EntityWithMetadata<HNItem> response = entityService.getById(id, modelSpec, HNItem.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting HNItem by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get HN item by Hacker News ID
     * GET /api/hnitem/hn/{hnId}
     */
    @GetMapping("/hn/{hnId}")
    public ResponseEntity<EntityWithMetadata<HNItem>> getEntityByHNId(@PathVariable Long hnId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            EntityWithMetadata<HNItem> response = entityService.findByBusinessId(
                    modelSpec, hnId.toString(), "id", HNItem.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting HNItem by HN ID: {}", hnId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update HN item with optional workflow transition
     * PUT /api/hnitem/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<HNItem>> updateEntity(
            @PathVariable UUID id,
            @RequestBody HNItem entity,
            @RequestParam(required = false) String transition) {
        try {
            entity.setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<HNItem> response = entityService.update(id, entity, transition);
            logger.info("HNItem updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating HNItem", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete HN item by technical UUID
     * DELETE /api/hnitem/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntity(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("HNItem deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting HNItem", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // DTOs for batch operations
    @Getter
    @Setter
    public static class BatchCreateResponse {
        private List<EntityWithMetadata<HNItem>> successful;
        private List<FailedItem> failed;
        private BatchSummary summary;
    }

    @Getter
    @Setter
    public static class FailedItem {
        private HNItem item;
        private String error;

        public FailedItem() {}

        public FailedItem(HNItem item, String error) {
            this.item = item;
            this.error = error;
        }
    }

    @Getter
    @Setter
    public static class BatchSummary {
        private int total;
        private int successful;
        private int failed;

        public BatchSummary() {}

        public BatchSummary(int total, int successful, int failed) {
            this.total = total;
            this.successful = successful;
            this.failed = failed;
        }
    }
}

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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HNItemController - REST controller for managing Hacker News items
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
     * Create a new HN item
     * POST /api/hnitem
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<HNItem>> createHNItem(@RequestBody HNItem hnItem) {
        try {
            logger.info("Creating HNItem with ID: {}", hnItem.getId());
            
            // Validate the item
            if (!hnItem.isValid()) {
                logger.error("Invalid HNItem data: ID={}, Type={}", hnItem.getId(), hnItem.getType());
                return ResponseEntity.badRequest().build();
            }

            // Create the entity - transition will be auto_create (automatic)
            EntityWithMetadata<HNItem> response = entityService.create(hnItem);
            logger.info("HNItem created with technical ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating HNItem", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get HN item by technical UUID (FASTEST method)
     * GET /api/hnitem/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<HNItem>> getHNItemById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            EntityWithMetadata<HNItem> response = entityService.getById(id, modelSpec, HNItem.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting HNItem by technical ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get HN item by business ID (HN item ID)
     * GET /api/hnitem/business/{hnItemId}
     */
    @GetMapping("/business/{hnItemId}")
    public ResponseEntity<EntityWithMetadata<HNItem>> getHNItemByBusinessId(@PathVariable Integer hnItemId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            EntityWithMetadata<HNItem> response = entityService.findByBusinessId(
                    modelSpec, hnItemId.toString(), "id", HNItem.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting HNItem by business ID: {}", hnItemId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update existing HN item
     * PUT /api/hnitem/{id}?transition=update_item
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<HNItem>> updateHNItem(
            @PathVariable UUID id,
            @RequestBody HNItem hnItem,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating HNItem with technical ID: {}", id);

            // Validate the item
            if (!hnItem.isValid()) {
                logger.error("Invalid HNItem data for update: ID={}, Type={}", hnItem.getId(), hnItem.getType());
                return ResponseEntity.badRequest().build();
            }

            // Update the entity with optional transition
            EntityWithMetadata<HNItem> response = entityService.update(id, hnItem, transition);
            logger.info("HNItem updated with technical ID: {}", id);
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
    public ResponseEntity<Void> deleteHNItem(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("HNItem deleted with technical ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting HNItem", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all HN items (USE SPARINGLY)
     * GET /api/hnitem
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<HNItem>>> getAllHNItems() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            List<EntityWithMetadata<HNItem>> items = entityService.findAll(modelSpec, HNItem.class);
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            logger.error("Error getting all HNItems", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search HN items by type
     * GET /api/hnitem/search/type?type=story
     */
    @GetMapping("/search/type")
    public ResponseEntity<List<EntityWithMetadata<HNItem>>> searchHNItemsByType(@RequestParam String type) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.type")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(type));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<HNItem>> items = entityService.search(modelSpec, condition, HNItem.class);
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            logger.error("Error searching HNItems by type: {}", type, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search HN items by author
     * GET /api/hnitem/search/author?by=username
     */
    @GetMapping("/search/author")
    public ResponseEntity<List<EntityWithMetadata<HNItem>>> searchHNItemsByAuthor(@RequestParam String by) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.by")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(by));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<HNItem>> items = entityService.search(modelSpec, condition, HNItem.class);
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            logger.error("Error searching HNItems by author: {}", by, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Trigger Firebase HN API data pull
     * POST /api/hnitem/firebase-sync
     */
    @PostMapping("/firebase-sync")
    public ResponseEntity<FirebaseSyncResponse> triggerFirebaseSync(@RequestBody FirebaseSyncRequest request) {
        try {
            logger.info("Triggering Firebase sync for {} items", request.getItemIds().size());
            
            List<EntityWithMetadata<HNItem>> createdItems = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            
            // This is a placeholder implementation - in a real system, you would:
            // 1. Fetch data from Firebase HN API
            // 2. Create HNItem entities for each fetched item
            // 3. Return the results
            
            for (Integer itemId : request.getItemIds()) {
                try {
                    // Placeholder: Create a basic HNItem structure
                    // In reality, you would fetch from Firebase API
                    HNItem hnItem = new HNItem();
                    hnItem.setId(itemId);
                    hnItem.setType("story"); // Default type
                    
                    EntityWithMetadata<HNItem> created = entityService.create(hnItem);
                    createdItems.add(created);
                } catch (Exception e) {
                    errors.add("Failed to sync item " + itemId + ": " + e.getMessage());
                }
            }
            
            FirebaseSyncResponse response = new FirebaseSyncResponse();
            response.setSyncedItems(createdItems.size());
            response.setFailedItems(errors.size());
            response.setErrors(errors);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error triggering Firebase sync", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request/Response DTOs

    @Getter
    @Setter
    public static class FirebaseSyncRequest {
        private List<Integer> itemIds;
        private String syncType;
    }

    @Getter
    @Setter
    public static class FirebaseSyncResponse {
        private int syncedItems;
        private int failedItems;
        private List<String> errors;
    }
}

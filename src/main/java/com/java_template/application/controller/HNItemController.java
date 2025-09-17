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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HNItemController - REST controller for managing Hacker News items
 * 
 * Supports creating single items, arrays of items, and retrieving items with search capabilities.
 */
@RestController
@RequestMapping("/api/hnitems")
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
     * POST /api/hnitems
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createEntity(@RequestBody HNItem entity) {
        try {
            EntityWithMetadata<HNItem> response = entityService.create(entity);
            logger.info("HNItem created with ID: {}", response.metadata().getId());
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("uuid", response.metadata().getId());
            data.put("entity", response.entity());
            Map<String, Object> meta = new HashMap<>();
            meta.put("state", response.metadata().getState());
            meta.put("createdAt", response.metadata().getCreationDate());
            data.put("meta", meta);
            result.put("data", data);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error creating HNItem", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("code", "CREATION_ERROR");
            errorDetails.put("message", e.getMessage());
            error.put("error", errorDetails);
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Create multiple HN items
     * POST /api/hnitems/batch
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> createBatch(@RequestBody BatchRequest request) {
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            int created = 0;
            int failed = 0;

            for (HNItem item : request.getItems()) {
                try {
                    EntityWithMetadata<HNItem> response = entityService.create(item);
                    Map<String, Object> itemResult = new HashMap<>();
                    itemResult.put("uuid", response.metadata().getId());
                    itemResult.put("entity", response.entity());
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("state", response.metadata().getState());
                    meta.put("createdAt", response.metadata().getCreationDate());
                    itemResult.put("meta", meta);
                    items.add(itemResult);
                    created++;
                } catch (Exception e) {
                    failed++;
                    logger.warn("Failed to create item {}: {}", item.getId(), e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("created", created);
            data.put("failed", failed);
            data.put("items", items);
            result.put("data", data);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error creating batch HNItems", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("code", "BATCH_CREATION_ERROR");
            errorDetails.put("message", e.getMessage());
            error.put("error", errorDetails);
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get entity by technical UUID
     * GET /api/hnitems/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEntityById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            EntityWithMetadata<HNItem> response = entityService.getById(id, modelSpec, HNItem.class);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("uuid", response.metadata().getId());
            data.put("entity", response.entity());
            Map<String, Object> meta = new HashMap<>();
            meta.put("state", response.metadata().getState());
            meta.put("createdAt", response.metadata().getCreationDate());
            data.put("meta", meta);
            result.put("data", data);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting HNItem by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update entity with optional workflow transition
     * PUT /api/hnitems/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateEntity(
            @PathVariable UUID id,
            @RequestBody HNItem entity,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<HNItem> response = entityService.update(id, entity, transition);
            logger.info("HNItem updated with ID: {}", id);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("uuid", response.metadata().getId());
            data.put("entity", response.entity());
            Map<String, Object> meta = new HashMap<>();
            meta.put("state", response.metadata().getState());
            meta.put("createdAt", response.metadata().getCreationDate());
            data.put("meta", meta);
            result.put("data", data);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating HNItem", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("code", "UPDATE_ERROR");
            errorDetails.put("message", e.getMessage());
            error.put("error", errorDetails);
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Search and list HN items with filtering
     * GET /api/hnitems
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> searchEntities(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer maxScore,
            @RequestParam(required = false) Long fromTime,
            @RequestParam(required = false) Long toTime,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (type != null && !type.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.type")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(type)));
            }

            if (author != null && !author.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.by")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(author)));
            }

            if (minScore != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.score")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(minScore)));
            }

            if (maxScore != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.score")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(maxScore)));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<HNItem>> entities = entityService.search(modelSpec, condition, HNItem.class);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("items", entities);
            data.put("total", entities.size());
            data.put("limit", limit);
            data.put("offset", offset);
            result.put("data", data);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error searching HNItems", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("code", "SEARCH_ERROR");
            errorDetails.put("message", e.getMessage());
            error.put("error", errorDetails);
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Trigger pulling data from Firebase HN API
     * POST /api/hnitems/pull-firebase
     */
    @PostMapping("/pull-firebase")
    public ResponseEntity<Map<String, Object>> pullFromFirebase(@RequestBody PullRequest request) {
        try {
            // Simulated Firebase pull operation
            String pullId = "pull-" + UUID.randomUUID();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("pullId", pullId);
            data.put("itemIds", request.getItemIds());
            data.put("status", "initiated");
            result.put("data", data);

            logger.info("Firebase pull initiated with ID: {}", pullId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error initiating Firebase pull", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("code", "PULL_ERROR");
            errorDetails.put("message", e.getMessage());
            error.put("error", errorDetails);
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Request DTOs
    @Getter
    @Setter
    public static class BatchRequest {
        private List<HNItem> items;
    }

    @Getter
    @Setter
    public static class PullRequest {
        private List<Long> itemIds;
        private String pullType;
    }
}

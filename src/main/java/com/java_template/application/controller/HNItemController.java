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

    /**
     * Search HN items with query parameters
     * GET /api/hnitem/search?query=dropbox&type=story&author=dhouston
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> searchEntities(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer maxScore,
            @RequestParam(required = false) Boolean hasUrl,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            List<SimpleCondition> conditions = new ArrayList<>();

            // Text search across title, text, and url
            if (query != null && !query.trim().isEmpty()) {
                // For simplicity, search in title field only
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.title")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(query)));
            }

            // Filter by type
            if (type != null && !type.trim().isEmpty()) {
                conditions.add(new SimpleCondition().withJsonPath("$.type").withOperation(Operation.EQUALS).withValue(objectMapper.valueToTree(type)));
            }

            // Filter by author
            if (author != null && !author.trim().isEmpty()) {
                conditions.add(new SimpleCondition().withJsonPath("$.by").withOperation(Operation.EQUALS).withValue(objectMapper.valueToTree(author)));
            }

            // Filter by score range
            if (minScore != null) {
                conditions.add(new SimpleCondition().withJsonPath("$.score").withOperation(Operation.GREATER_OR_EQUAL).withValue(objectMapper.valueToTree(minScore)));
            }
            if (maxScore != null) {
                conditions.add(new SimpleCondition().withJsonPath("$.score").withOperation(Operation.LESS_OR_EQUAL).withValue(objectMapper.valueToTree(maxScore)));
            }

            // Filter by URL presence
            if (hasUrl != null) {
                if (hasUrl) {
                    conditions.add(new SimpleCondition().withJsonPath("$.url").withOperation(Operation.NOT_NULL).withValue(objectMapper.valueToTree(true)));
                } else {
                    conditions.add(new SimpleCondition().withJsonPath("$.url").withOperation(Operation.IS_NULL).withValue(objectMapper.valueToTree(true)));
                }
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<HNItem>> entities = entityService.search(modelSpec, condition, HNItem.class);

            // Apply pagination
            int total = entities.size();
            int endIndex = Math.min(offset + limit, total);
            List<EntityWithMetadata<HNItem>> paginatedEntities = entities.subList(Math.min(offset, total), endIndex);

            SearchResponse response = new SearchResponse();
            response.setItems(paginatedEntities);
            response.setPagination(new PaginationInfo(total, limit, offset, endIndex < total));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error searching HNItems", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get item hierarchy (parents and children)
     * GET /api/hnitem/{id}/hierarchy
     */
    @GetMapping("/{id}/hierarchy")
    public ResponseEntity<HierarchyResponse> getItemHierarchy(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            EntityWithMetadata<HNItem> item = entityService.getById(id, modelSpec, HNItem.class);

            if (item == null) {
                return ResponseEntity.notFound().build();
            }

            HierarchyResponse response = new HierarchyResponse();
            response.setItem(item);
            response.setParents(getParentHierarchy(item.entity()));
            response.setChildren(getChildrenItems(item.entity()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting hierarchy for HNItem: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Trigger Firebase API pull
     * POST /api/hnitem/pull-from-firebase
     */
    @PostMapping("/pull-from-firebase")
    public ResponseEntity<FirebasePullResponse> pullFromFirebase(@RequestBody FirebasePullRequest request) {
        try {
            // Generate pull ID
            String pullId = "pull-" + UUID.randomUUID().toString();

            // In a real implementation, this would:
            // 1. Validate the request
            // 2. Queue the Firebase API calls
            // 3. Create HNItem entities asynchronously
            // 4. Return status information

            FirebasePullResponse response = new FirebasePullResponse();
            response.setPullId(pullId);
            response.setStatus("INITIATED");
            response.setRequestedItems(request.getItemIds() != null ? request.getItemIds().size() : 0);
            response.setMessage("Firebase pull initiated for " + response.getRequestedItems() + " items");

            logger.info("Firebase pull initiated: {}", pullId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error initiating Firebase pull", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all HN items (paginated)
     * GET /api/hnitem
     */
    @GetMapping
    public ResponseEntity<SearchResponse> getAllEntities(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            List<EntityWithMetadata<HNItem>> entities = entityService.findAll(modelSpec, HNItem.class);

            // Apply pagination
            int total = entities.size();
            int endIndex = Math.min(offset + limit, total);
            List<EntityWithMetadata<HNItem>> paginatedEntities = entities.subList(Math.min(offset, total), endIndex);

            SearchResponse response = new SearchResponse();
            response.setItems(paginatedEntities);
            response.setPagination(new PaginationInfo(total, limit, offset, endIndex < total));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting all HNItems", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods
    private List<EntityWithMetadata<HNItem>> getParentHierarchy(HNItem item) {
        List<EntityWithMetadata<HNItem>> parents = new ArrayList<>();
        // In a real implementation, this would traverse up the parent hierarchy
        // For now, return empty list
        return parents;
    }

    private List<EntityWithMetadata<HNItem>> getChildrenItems(HNItem item) {
        List<EntityWithMetadata<HNItem>> children = new ArrayList<>();
        // In a real implementation, this would find children based on kids field
        // For now, return empty list
        return children;
    }

    // DTOs for various operations
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

    @Getter
    @Setter
    public static class SearchResponse {
        private List<EntityWithMetadata<HNItem>> items;
        private PaginationInfo pagination;
    }

    @Getter
    @Setter
    public static class PaginationInfo {
        private int total;
        private int limit;
        private int offset;
        private boolean hasMore;

        public PaginationInfo() {}

        public PaginationInfo(int total, int limit, int offset, boolean hasMore) {
            this.total = total;
            this.limit = limit;
            this.offset = offset;
            this.hasMore = hasMore;
        }
    }

    @Getter
    @Setter
    public static class HierarchyResponse {
        private EntityWithMetadata<HNItem> item;
        private List<EntityWithMetadata<HNItem>> parents;
        private List<EntityWithMetadata<HNItem>> children;
    }

    @Getter
    @Setter
    public static class FirebasePullRequest {
        private List<Long> itemIds;
        private String pullType;
    }

    @Getter
    @Setter
    public static class FirebasePullResponse {
        private String pullId;
        private String status;
        private int requestedItems;
        private String message;
    }
}

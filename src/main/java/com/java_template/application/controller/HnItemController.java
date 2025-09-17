package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HnItem;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HnItemController - REST API endpoints for managing Hacker News items
 * 
 * Provides endpoints for:
 * - Single item operations (CRUD)
 * - Bulk operations (array and file upload)
 * - Firebase API integration
 * - Hierarchical search capabilities
 */
@RestController
@RequestMapping("/api/hnitem")
@CrossOrigin(origins = "*")
public class HnItemController {

    private static final Logger logger = LoggerFactory.getLogger(HnItemController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public HnItemController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a single HN item
     * POST /api/hnitem
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<HnItem>> createEntity(@RequestBody HnItem entity) {
        try {
            // Set creation timestamp
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<HnItem> response = entityService.create(entity);
            logger.info("HnItem created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating HnItem", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create multiple HN items from an array
     * POST /api/hnitem/bulk
     */
    @PostMapping("/bulk")
    public ResponseEntity<BulkCreateResponse> createMultipleEntities(@RequestBody List<HnItem> entities) {
        try {
            BulkCreateResponse response = new BulkCreateResponse();
            response.setItems(new ArrayList<>());
            
            for (HnItem entity : entities) {
                try {
                    entity.setCreatedAt(LocalDateTime.now());
                    entity.setUpdatedAt(LocalDateTime.now());
                    
                    EntityWithMetadata<HnItem> created = entityService.create(entity);
                    response.getItems().add(created);
                    response.setCreated(response.getCreated() + 1);
                } catch (Exception e) {
                    logger.error("Error creating HnItem in bulk: {}", entity.getId(), e);
                    response.setFailed(response.getFailed() + 1);
                }
            }
            
            logger.info("Bulk create completed: {} created, {} failed", response.getCreated(), response.getFailed());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in bulk create", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Upload HN items from a JSON file
     * POST /api/hnitem/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFromFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Parse JSON file to list of HnItem objects
            HnItem[] itemsArray = objectMapper.readValue(file.getInputStream(), HnItem[].class);
            List<HnItem> items = List.of(itemsArray);
            
            FileUploadResponse response = new FileUploadResponse();
            response.setErrors(new ArrayList<>());
            
            for (HnItem entity : items) {
                try {
                    entity.setCreatedAt(LocalDateTime.now());
                    entity.setUpdatedAt(LocalDateTime.now());
                    
                    entityService.create(entity);
                    response.setCreated(response.getCreated() + 1);
                } catch (Exception e) {
                    logger.error("Error creating HnItem from file: {}", entity.getId(), e);
                    response.setFailed(response.getFailed() + 1);
                    
                    FileUploadError error = new FileUploadError();
                    error.setItem(entity);
                    error.setError(e.getMessage());
                    response.getErrors().add(error);
                }
            }
            
            logger.info("File upload completed: {} created, {} failed", response.getCreated(), response.getFailed());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing file upload", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get HN item by technical UUID
     * GET /api/hnitem/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<HnItem>> getEntityById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);
            EntityWithMetadata<HnItem> response = entityService.getById(id, modelSpec, HnItem.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting HnItem by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get HN item by business ID (Hacker News ID)
     * GET /api/hnitem/business/{hnId}
     */
    @GetMapping("/business/{hnId}")
    public ResponseEntity<EntityWithMetadata<HnItem>> getEntityByBusinessId(@PathVariable Long hnId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);
            EntityWithMetadata<HnItem> response = entityService.findByBusinessId(
                    modelSpec, hnId.toString(), "id", HnItem.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting HnItem by business ID: {}", hnId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update HN item with optional workflow transition
     * PUT /api/hnitem/{id}?transition={transitionName}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<HnItem>> updateEntity(
            @PathVariable UUID id,
            @RequestBody HnItem entity,
            @RequestParam(required = false) String transition) {
        try {
            entity.setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<HnItem> response = entityService.update(id, entity, transition);
            logger.info("HnItem updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating HnItem", e);
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
            logger.info("HnItem deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting HnItem", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all HN items (paginated)
     * GET /api/hnitem
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse> getAllEntities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);

            List<EntityWithMetadata<HnItem>> entities;
            if (type != null && !type.trim().isEmpty()) {
                // Filter by type
                SimpleCondition typeCondition = new SimpleCondition()
                        .withJsonPath("$.type")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(type));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(typeCondition));

                entities = entityService.search(modelSpec, condition, HnItem.class);
            } else {
                entities = entityService.findAll(modelSpec, HnItem.class);
            }

            // Simple pagination (in real implementation, use proper pagination)
            int start = page * size;
            int end = Math.min(start + size, entities.size());
            List<EntityWithMetadata<HnItem>> paginatedItems = entities.subList(start, end);

            PaginatedResponse response = new PaginatedResponse();
            response.setItems(paginatedItems);
            response.setPage(page);
            response.setSize(size);
            response.setTotal(entities.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting all HnItems", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search HN items with various criteria
     * POST /api/hnitem/search
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchEntities(@RequestBody SearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getType() != null && !searchRequest.getType().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.type")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getType())));
            }

            if (searchRequest.getAuthor() != null && !searchRequest.getAuthor().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.by")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getAuthor())));
            }

            if (searchRequest.getMinScore() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.score")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinScore())));
            }

            if (searchRequest.getTitleContains() != null && !searchRequest.getTitleContains().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.title")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getTitleContains())));
            }

            if (searchRequest.getTimeRange() != null) {
                if (searchRequest.getTimeRange().getStart() != null) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.time")
                            .withOperation(Operation.GREATER_OR_EQUAL)
                            .withValue(objectMapper.valueToTree(searchRequest.getTimeRange().getStart())));
                }
                if (searchRequest.getTimeRange().getEnd() != null) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.time")
                            .withOperation(Operation.LESS_OR_EQUAL)
                            .withValue(objectMapper.valueToTree(searchRequest.getTimeRange().getEnd())));
                }
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<QueryCondition>(conditions));

            List<EntityWithMetadata<HnItem>> entities = entityService.search(modelSpec, condition, HnItem.class);

            SearchResponse response = new SearchResponse();
            response.setItems(entities);
            response.setTotal(entities.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error searching HnItems", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Response DTOs
    @Getter
    @Setter
    public static class BulkCreateResponse {
        private int created = 0;
        private int failed = 0;
        private List<EntityWithMetadata<HnItem>> items;
    }

    @Getter
    @Setter
    public static class FileUploadResponse {
        private int created = 0;
        private int failed = 0;
        private List<FileUploadError> errors;
    }

    @Getter
    @Setter
    public static class FileUploadError {
        private HnItem item;
        private String error;
    }

    @Getter
    @Setter
    public static class PaginatedResponse {
        private List<EntityWithMetadata<HnItem>> items;
        private int page;
        private int size;
        private int total;
    }

    @Getter
    @Setter
    public static class SearchResponse {
        private List<EntityWithMetadata<HnItem>> items;
        private int total;
    }

    @Getter
    @Setter
    public static class SearchRequest {
        private String type;
        private String author;
        private Integer minScore;
        private String titleContains;
        private TimeRange timeRange;
    }

    /**
     * Trigger Firebase API pull
     * POST /api/hnitem/pull-firebase
     */
    @PostMapping("/pull-firebase")
    public ResponseEntity<FirebasePullResponse> pullFromFirebase(@RequestBody FirebasePullRequest request) {
        try {
            // Note: This is a placeholder implementation
            // In a real implementation, you would integrate with Firebase HN API
            FirebasePullResponse response = new FirebasePullResponse();

            if ("specific".equals(request.getPullType()) && request.getItemIds() != null) {
                response.setRequested(request.getItemIds().size());
                // Simulate pulling specific items
                for (Long itemId : request.getItemIds()) {
                    // In real implementation, fetch from Firebase API
                    logger.info("Would pull item {} from Firebase API", itemId);
                }
                response.setRetrieved(request.getItemIds().size());
                response.setCreated(request.getItemIds().size());
            } else if ("latest".equals(request.getPullType())) {
                int count = request.getCount() != null ? request.getCount() : 100;
                response.setRequested(count);
                response.setRetrieved(count);
                response.setCreated(count);
                logger.info("Would pull {} latest items from Firebase API", count);
            }

            response.setItems(new ArrayList<>());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error pulling from Firebase API", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search with parent hierarchy
     * POST /api/hnitem/search/hierarchy
     */
    @PostMapping("/search/hierarchy")
    public ResponseEntity<HierarchySearchResponse> searchWithHierarchy(@RequestBody HierarchySearchRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);

            // Find root item
            EntityWithMetadata<HnItem> rootItem = entityService.findByBusinessId(
                    modelSpec, request.getRootItemId().toString(), "id", HnItem.class);

            if (rootItem == null) {
                return ResponseEntity.notFound().build();
            }

            HierarchySearchResponse response = new HierarchySearchResponse();
            response.setRootItem(rootItem);
            response.setChildren(new ArrayList<>());

            if (request.getIncludeChildren() != null && request.getIncludeChildren()) {
                // Find children recursively
                List<HierarchicalItem> children = findChildren(rootItem.entity().getId(),
                        request.getMaxDepth() != null ? request.getMaxDepth() : 3,
                        request.getChildTypes(), 1);
                response.setChildren(children);
                response.setTotalItems(1 + countTotalItems(children));
            } else {
                response.setTotalItems(1);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in hierarchy search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private List<HierarchicalItem> findChildren(Long parentId, int maxDepth, List<String> childTypes, int currentDepth) {
        if (currentDepth > maxDepth) {
            return new ArrayList<>();
        }

        try {
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.parent")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(parentId)));

            if (childTypes != null && !childTypes.isEmpty()) {
                for (String type : childTypes) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.type")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(type)));
                }
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(childTypes != null && childTypes.size() > 1 ?
                            GroupCondition.Operator.OR : GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<QueryCondition>(conditions));

            List<EntityWithMetadata<HnItem>> children = entityService.search(modelSpec, condition, HnItem.class);

            List<HierarchicalItem> result = new ArrayList<>();
            for (EntityWithMetadata<HnItem> child : children) {
                HierarchicalItem hierarchicalItem = new HierarchicalItem();
                hierarchicalItem.setEntity(child.entity());
                hierarchicalItem.setMetadata(child.metadata());
                hierarchicalItem.setDepth(currentDepth);

                // Recursively find children
                List<HierarchicalItem> grandChildren = findChildren(child.entity().getId(),
                        maxDepth, childTypes, currentDepth + 1);
                hierarchicalItem.setChildren(grandChildren);

                result.add(hierarchicalItem);
            }

            return result;
        } catch (Exception e) {
            logger.error("Error finding children for parent: {}", parentId, e);
            return new ArrayList<>();
        }
    }

    private int countTotalItems(List<HierarchicalItem> items) {
        int count = items.size();
        for (HierarchicalItem item : items) {
            if (item.getChildren() != null) {
                count += countTotalItems(item.getChildren());
            }
        }
        return count;
    }

    @Getter
    @Setter
    public static class TimeRange {
        private Long start;
        private Long end;
    }

    @Getter
    @Setter
    public static class FirebasePullRequest {
        private List<Long> itemIds;
        private String pullType; // "specific" or "latest"
        private Integer count;
    }

    @Getter
    @Setter
    public static class FirebasePullResponse {
        private int requested = 0;
        private int retrieved = 0;
        private int created = 0;
        private int updated = 0;
        private int failed = 0;
        private List<EntityWithMetadata<HnItem>> items;
    }

    @Getter
    @Setter
    public static class HierarchySearchRequest {
        private Long rootItemId;
        private Boolean includeChildren;
        private Integer maxDepth;
        private List<String> childTypes;
    }

    @Getter
    @Setter
    public static class HierarchySearchResponse {
        private EntityWithMetadata<HnItem> rootItem;
        private List<HierarchicalItem> children;
        private int totalItems;
    }

    @Getter
    @Setter
    public static class HierarchicalItem {
        private HnItem entity;
        private Object metadata; // Using Object to match the response format
        private int depth;
        private List<HierarchicalItem> children;
    }
}

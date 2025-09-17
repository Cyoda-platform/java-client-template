package com.java_template.application.controller;

import com.java_template.application.entity.searchquery.version_1.SearchQuery;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SearchQueryController - REST controller for managing search queries
 * 
 * Supports complex filtering and parent hierarchy joins for comprehensive search capabilities.
 */
@RestController
@RequestMapping("/api/search")
@CrossOrigin(origins = "*")
public class SearchQueryController {

    private static final Logger logger = LoggerFactory.getLogger(SearchQueryController.class);
    private final EntityService entityService;

    public SearchQueryController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create and execute a search query
     * POST /api/search
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createSearchQuery(@RequestBody SearchQueryRequest request) {
        try {
            // Create SearchQuery entity from request
            SearchQuery searchQuery = new SearchQuery();
            searchQuery.setQueryId("search-" + System.currentTimeMillis());
            searchQuery.setSearchText(request.getSearchText());
            searchQuery.setItemType(request.getItemType());
            searchQuery.setAuthor(request.getAuthor());
            searchQuery.setMinScore(request.getMinScore());
            searchQuery.setMaxScore(request.getMaxScore());
            searchQuery.setFromTime(request.getFromTime());
            searchQuery.setToTime(request.getToTime());
            searchQuery.setIncludeParentHierarchy(request.getIncludeParentHierarchy());
            searchQuery.setMaxDepth(request.getMaxDepth());
            searchQuery.setSortBy(request.getSortBy());
            searchQuery.setSortOrder(request.getSortOrder());
            searchQuery.setLimit(request.getLimit());
            searchQuery.setOffset(request.getOffset());

            EntityWithMetadata<SearchQuery> response = entityService.create(searchQuery);
            logger.info("SearchQuery created with ID: {}", response.metadata().getId());

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
            logger.error("Error creating SearchQuery", e);
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
     * Retrieve search query and its results
     * GET /api/search/{uuid}
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<Map<String, Object>> getSearchQuery(@PathVariable UUID uuid) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(SearchQuery.ENTITY_NAME).withVersion(SearchQuery.ENTITY_VERSION);
            EntityWithMetadata<SearchQuery> response = entityService.getById(uuid, modelSpec, SearchQuery.class);

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
            logger.error("Error getting SearchQuery by ID: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update search query and re-execute
     * PUT /api/search/{uuid}?transition=retry
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<Map<String, Object>> updateSearchQuery(
            @PathVariable UUID uuid,
            @RequestBody SearchQueryUpdateRequest request,
            @RequestParam(required = false) String transition) {
        try {
            // Get existing search query
            ModelSpec modelSpec = new ModelSpec().withName(SearchQuery.ENTITY_NAME).withVersion(SearchQuery.ENTITY_VERSION);
            EntityWithMetadata<SearchQuery> existing = entityService.getById(uuid, modelSpec, SearchQuery.class);
            
            SearchQuery searchQuery = existing.entity();
            
            // Update fields if provided
            if (request.getSearchText() != null) {
                searchQuery.setSearchText(request.getSearchText());
            }
            if (request.getMinScore() != null) {
                searchQuery.setMinScore(request.getMinScore());
            }
            if (request.getMaxScore() != null) {
                searchQuery.setMaxScore(request.getMaxScore());
            }
            if (request.getLimit() != null) {
                searchQuery.setLimit(request.getLimit());
            }

            EntityWithMetadata<SearchQuery> response = entityService.update(uuid, searchQuery, transition);
            logger.info("SearchQuery updated with ID: {}", uuid);

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
            logger.error("Error updating SearchQuery", e);
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
     * List search queries with filtering
     * GET /api/search
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listSearchQueries(
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(SearchQuery.ENTITY_NAME).withVersion(SearchQuery.ENTITY_VERSION);
            List<EntityWithMetadata<SearchQuery>> entities = entityService.findAll(modelSpec, SearchQuery.class);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("queries", entities);
            data.put("total", entities.size());
            data.put("limit", limit);
            data.put("offset", offset);
            result.put("data", data);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error listing SearchQueries", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("code", "LIST_ERROR");
            errorDetails.put("message", e.getMessage());
            error.put("error", errorDetails);
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Request DTOs
    @Getter
    @Setter
    public static class SearchQueryRequest {
        private String searchText;
        private String itemType;
        private String author;
        private Integer minScore;
        private Integer maxScore;
        private Long fromTime;
        private Long toTime;
        private Boolean includeParentHierarchy;
        private Integer maxDepth;
        private String sortBy;
        private String sortOrder;
        private Integer limit;
        private Integer offset;
    }

    @Getter
    @Setter
    public static class SearchQueryUpdateRequest {
        private String searchText;
        private Integer minScore;
        private Integer maxScore;
        private Integer limit;
    }
}

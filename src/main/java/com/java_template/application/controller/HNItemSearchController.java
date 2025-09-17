package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.application.entity.hnitemsearch.version_1.HNItemSearch;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HNItemSearchController - REST controller for managing HN item searches
 */
@RestController
@RequestMapping("/api/hnitem/search")
@CrossOrigin(origins = "*")
public class HNItemSearchController {

    private static final Logger logger = LoggerFactory.getLogger(HNItemSearchController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public HNItemSearchController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute search query for HN items
     * POST /api/hnitem/search
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<HNItemSearch>> createSearch(@RequestBody SearchRequest request) {
        try {
            logger.info("Creating search with query: {} and type: {}", request.getQuery(), request.getSearchType());
            
            // Create HNItemSearch entity
            HNItemSearch searchEntity = new HNItemSearch();
            searchEntity.setSearchId(generateSearchId());
            searchEntity.setQuery(request.getQuery());
            searchEntity.setSearchType(request.getSearchType());
            searchEntity.setFilters(request.getFilters());
            searchEntity.setIncludeParents(request.getIncludeParents());
            searchEntity.setMaxResults(request.getMaxResults());

            // Validate the search entity
            if (!searchEntity.isValid()) {
                logger.error("Invalid search request: query={}, searchType={}", 
                           request.getQuery(), request.getSearchType());
                return ResponseEntity.badRequest().build();
            }

            // Create the entity - transition will be auto_create (automatic)
            EntityWithMetadata<HNItemSearch> response = entityService.create(searchEntity);
            logger.info("Search created with technical ID: {} and searchId: {}", 
                       response.metadata().getId(), searchEntity.getSearchId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get search by technical UUID
     * GET /api/hnitem/search/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<HNItemSearch>> getSearchById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItemSearch.ENTITY_NAME).withVersion(HNItemSearch.ENTITY_VERSION);
            EntityWithMetadata<HNItemSearch> response = entityService.getById(id, modelSpec, HNItemSearch.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting search by technical ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get search by business ID (searchId)
     * GET /api/hnitem/search/business/{searchId}
     */
    @GetMapping("/business/{searchId}")
    public ResponseEntity<EntityWithMetadata<HNItemSearch>> getSearchByBusinessId(@PathVariable String searchId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItemSearch.ENTITY_NAME).withVersion(HNItemSearch.ENTITY_VERSION);
            EntityWithMetadata<HNItemSearch> response = entityService.findByBusinessId(
                    modelSpec, searchId, "searchId", HNItemSearch.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting search by business ID: {}", searchId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search with parent hierarchy joins
     * POST /api/hnitem/search/hierarchical
     */
    @PostMapping("/hierarchical")
    public ResponseEntity<EntityWithMetadata<HNItemSearch>> createHierarchicalSearch(@RequestBody SearchRequest request) {
        try {
            logger.info("Creating hierarchical search with query: {}", request.getQuery());
            
            // Force search type to hierarchical
            request.setSearchType("hierarchical");
            
            // Create HNItemSearch entity
            HNItemSearch searchEntity = new HNItemSearch();
            searchEntity.setSearchId(generateSearchId());
            searchEntity.setQuery(request.getQuery());
            searchEntity.setSearchType(request.getSearchType());
            searchEntity.setFilters(request.getFilters());
            searchEntity.setIncludeParents(true); // Always include parents for hierarchical search
            searchEntity.setMaxResults(request.getMaxResults());

            // Validate the search entity
            if (!searchEntity.isValid()) {
                logger.error("Invalid hierarchical search request: query={}", request.getQuery());
                return ResponseEntity.badRequest().build();
            }

            // Create the entity
            EntityWithMetadata<HNItemSearch> response = entityService.create(searchEntity);
            logger.info("Hierarchical search created with technical ID: {} and searchId: {}", 
                       response.metadata().getId(), searchEntity.getSearchId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating hierarchical search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get detailed search results with items
     * GET /api/hnitem/search/{searchId}/results
     */
    @GetMapping("/{searchId}/results")
    public ResponseEntity<SearchResultsResponse> getSearchResults(@PathVariable String searchId) {
        try {
            // First, find the search entity
            ModelSpec searchModelSpec = new ModelSpec().withName(HNItemSearch.ENTITY_NAME).withVersion(HNItemSearch.ENTITY_VERSION);
            EntityWithMetadata<HNItemSearch> searchResponse = entityService.findByBusinessId(
                    searchModelSpec, searchId, "searchId", HNItemSearch.class);

            if (searchResponse == null) {
                return ResponseEntity.notFound().build();
            }

            HNItemSearch searchEntity = searchResponse.entity();
            
            // For this implementation, we'll return a placeholder response
            // In a real system, you would store and retrieve the actual search results
            SearchResultsResponse response = new SearchResultsResponse();
            response.setSearchId(searchId);
            response.setTotalResults(searchEntity.getResultCount() != null ? searchEntity.getResultCount() : 0);
            response.setExecutionTimeMs(searchEntity.getExecutionTimeMs() != null ? searchEntity.getExecutionTimeMs() : 0);
            
            // Placeholder: In reality, you would fetch the actual HNItem results
            // based on the search criteria and return them here
            response.setResults(List.of()); // Empty for now
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting search results for searchId: {}", searchId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all searches (USE SPARINGLY)
     * GET /api/hnitem/search
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<HNItemSearch>>> getAllSearches() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItemSearch.ENTITY_NAME).withVersion(HNItemSearch.ENTITY_VERSION);
            List<EntityWithMetadata<HNItemSearch>> searches = entityService.findAll(modelSpec, HNItemSearch.class);
            return ResponseEntity.ok(searches);
        } catch (Exception e) {
            logger.error("Error getting all searches", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search searches by type
     * GET /api/hnitem/search/type?searchType=text
     */
    @GetMapping("/type")
    public ResponseEntity<List<EntityWithMetadata<HNItemSearch>>> getSearchesByType(@RequestParam String searchType) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItemSearch.ENTITY_NAME).withVersion(HNItemSearch.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.searchType")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(searchType));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<HNItemSearch>> searches = entityService.search(modelSpec, condition, HNItemSearch.class);
            return ResponseEntity.ok(searches);
        } catch (Exception e) {
            logger.error("Error searching by search type: {}", searchType, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate a unique search ID
     */
    private String generateSearchId() {
        return "search-" + System.currentTimeMillis();
    }

    // Request/Response DTOs

    @Getter
    @Setter
    public static class SearchRequest {
        private String query;
        private String searchType;
        private Map<String, Object> filters;
        private Boolean includeParents;
        private Integer maxResults;
    }

    @Getter
    @Setter
    public static class SearchResultsResponse {
        private String searchId;
        private List<HNItemResult> results;
        private Integer totalResults;
        private Long executionTimeMs;
    }

    @Getter
    @Setter
    public static class HNItemResult {
        private Integer id;
        private String type;
        private String title;
        private Integer score;
        private List<HNItemResult> parents;
    }
}

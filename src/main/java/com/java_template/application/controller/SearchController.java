package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.search_query.version_1.SearchQuery;
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
 * SearchController - REST API for search query management and execution
 * 
 * This controller provides endpoints for:
 * - Creating and executing search queries
 * - Tracking search interactions
 * - Retrieving search history
 * - Managing search filters and results
 */
@RestController
@RequestMapping("/ui/search")
@CrossOrigin(origins = "*")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SearchController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create and execute a new search query
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<SearchQuery>> createSearch(@RequestBody SearchRequest searchRequest) {
        logger.info("Creating new search query: {}", searchRequest.getSearchTerm());

        try {
            // Create SearchQuery entity
            SearchQuery searchQuery = new SearchQuery();
            searchQuery.setQueryId(UUID.randomUUID().toString());
            searchQuery.setSearchTerm(searchRequest.getSearchTerm());
            searchQuery.setUserId(searchRequest.getUserId());
            searchQuery.setCreatedAt(LocalDateTime.now());

            // Set filters
            SearchQuery.SearchFilters filters = new SearchQuery.SearchFilters();
            filters.setGenres(searchRequest.getGenres());
            filters.setAuthors(searchRequest.getAuthors());
            filters.setPublicationYearStart(searchRequest.getPublicationYearStart());
            filters.setPublicationYearEnd(searchRequest.getPublicationYearEnd());
            filters.setLanguages(searchRequest.getLanguages());
            filters.setSortBy(searchRequest.getSortBy() != null ? searchRequest.getSortBy() : "relevance");
            filters.setSortOrder(searchRequest.getSortOrder() != null ? searchRequest.getSortOrder() : "desc");
            filters.setLimit(searchRequest.getLimit() != null ? searchRequest.getLimit() : 20);
            filters.setOffset(searchRequest.getOffset() != null ? searchRequest.getOffset() : 0);
            searchQuery.setFilters(filters);

            // Initialize execution details
            SearchQuery.SearchExecution execution = new SearchQuery.SearchExecution();
            execution.setStatus("pending");
            execution.setRetryCount(0);
            searchQuery.setExecution(execution);

            // Create the search query entity
            EntityWithMetadata<SearchQuery> result = entityService.create(searchQuery);

            // Execute the search (triggers SearchExecutionProcessor)
            EntityWithMetadata<SearchQuery> executedResult = entityService.updateWithManualTransition(
                createSearchQueryModelSpec(), searchQuery.getQueryId(), searchQuery, "execute_search");

            logger.info("Search query created and executed with ID: {}", result.getId());
            return ResponseEntity.ok(executedResult);

        } catch (Exception e) {
            logger.error("Failed to create search query: {}", searchRequest.getSearchTerm(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get search query by ID
     */
    @GetMapping("/{queryId}")
    public ResponseEntity<EntityWithMetadata<SearchQuery>> getSearch(@PathVariable String queryId) {
        logger.info("Retrieving search query with ID: {}", queryId);

        try {
            ModelSpec modelSpec = createSearchQueryModelSpec();
            EntityWithMetadata<SearchQuery> searchQuery = entityService.findByBusinessId(modelSpec, queryId, SearchQuery.class);

            if (searchQuery != null) {
                return ResponseEntity.ok(searchQuery);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Failed to retrieve search query with ID: {}", queryId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Track user interaction with search results
     */
    @PostMapping("/{queryId}/interaction")
    public ResponseEntity<UUID> trackInteraction(@PathVariable String queryId, 
                                                @RequestBody InteractionRequest interactionRequest) {
        logger.info("Tracking interaction for search query: {}", queryId);

        try {
            ModelSpec modelSpec = createSearchQueryModelSpec();
            
            // Get current search query
            EntityWithMetadata<SearchQuery> searchQueryEntity = entityService.findByBusinessId(modelSpec, queryId, SearchQuery.class);
            if (searchQueryEntity == null) {
                return ResponseEntity.notFound().build();
            }

            SearchQuery searchQuery = searchQueryEntity.entity();
            
            // Update interaction data
            SearchQuery.SearchInteraction interaction = searchQuery.getInteraction();
            if (interaction == null) {
                interaction = new SearchQuery.SearchInteraction();
                searchQuery.setInteraction(interaction);
            }

            // Update interaction details
            if (interactionRequest.getClickedBooks() != null) {
                interaction.setClickedBooks(interactionRequest.getClickedBooks());
            }
            if (interactionRequest.getBookmarkedBooks() != null) {
                interaction.setBookmarkedBooks(interactionRequest.getBookmarkedBooks());
            }
            if (interactionRequest.getViewDurationSeconds() != null) {
                interaction.setViewDurationSeconds(interactionRequest.getViewDurationSeconds());
            }
            if (interactionRequest.getSatisfactionScore() != null) {
                interaction.setSatisfactionScore(interactionRequest.getSatisfactionScore());
            }
            
            interaction.setLastInteractionAt(LocalDateTime.now());

            // Update with manual transition to trigger processor
            EntityWithMetadata<SearchQuery> result = entityService.updateWithManualTransition(
                modelSpec, queryId, searchQuery, "track_interaction");

            return ResponseEntity.ok(result.getId());

        } catch (Exception e) {
            logger.error("Failed to track interaction for search query: {}", queryId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get search history for a user
     */
    @GetMapping("/user/{userId}/history")
    public ResponseEntity<List<EntityWithMetadata<SearchQuery>>> getUserSearchHistory(@PathVariable String userId,
                                                                                     @RequestParam(defaultValue = "10") int limit) {
        logger.info("Retrieving search history for user: {} (limit: {})", userId, limit);

        try {
            ModelSpec modelSpec = createSearchQueryModelSpec();
            List<QueryCondition> conditions = new ArrayList<>();

            SimpleCondition userCondition = new SimpleCondition()
                    .withJsonPath("$.userId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(userId));
            conditions.add(userCondition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<SearchQuery>> results = entityService.search(modelSpec, groupCondition, SearchQuery.class);

            // Limit results (in a real implementation, this would be done in the query with sorting)
            if (results.size() > limit) {
                results = results.subList(0, limit);
            }

            return ResponseEntity.ok(results);

        } catch (Exception e) {
            logger.error("Failed to retrieve search history for user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get popular search terms
     */
    @GetMapping("/popular-terms")
    public ResponseEntity<List<String>> getPopularSearchTerms(@RequestParam(defaultValue = "10") int limit) {
        logger.info("Retrieving top {} popular search terms", limit);

        try {
            // In a real implementation, this would aggregate search terms from all SearchQuery entities
            // For now, we'll return a simulated list
            List<String> popularTerms = List.of(
                "science fiction", "mystery novels", "romance", "historical fiction",
                "fantasy", "thriller", "biography", "self help", "cooking", "travel"
            );

            return ResponseEntity.ok(popularTerms.subList(0, Math.min(limit, popularTerms.size())));

        } catch (Exception e) {
            logger.error("Failed to retrieve popular search terms", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Archive old search queries
     */
    @PostMapping("/{queryId}/archive")
    public ResponseEntity<UUID> archiveSearch(@PathVariable String queryId) {
        logger.info("Archiving search query: {}", queryId);

        try {
            ModelSpec modelSpec = createSearchQueryModelSpec();
            
            // Get current search query
            EntityWithMetadata<SearchQuery> searchQueryEntity = entityService.findByBusinessId(modelSpec, queryId, SearchQuery.class);
            if (searchQueryEntity == null) {
                return ResponseEntity.notFound().build();
            }

            SearchQuery searchQuery = searchQueryEntity.entity();

            // Archive with manual transition
            EntityWithMetadata<SearchQuery> result = entityService.updateWithManualTransition(
                modelSpec, queryId, searchQuery, "archive_search");

            return ResponseEntity.ok(result.getId());

        } catch (Exception e) {
            logger.error("Failed to archive search query: {}", queryId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private ModelSpec createSearchQueryModelSpec() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SearchQuery");
        modelSpec.setVersion(1);
        return modelSpec;
    }

    /**
     * Request DTO for creating search queries
     */
    @Getter
    @Setter
    public static class SearchRequest {
        private String searchTerm;
        private String userId;
        private List<String> genres;
        private List<String> authors;
        private Integer publicationYearStart;
        private Integer publicationYearEnd;
        private List<String> languages;
        private String sortBy;
        private String sortOrder;
        private Integer limit;
        private Integer offset;
    }

    /**
     * Request DTO for tracking search interactions
     */
    @Getter
    @Setter
    public static class InteractionRequest {
        private List<String> clickedBooks;
        private List<String> bookmarkedBooks;
        private Integer viewDurationSeconds;
        private Double satisfactionScore;
    }
}

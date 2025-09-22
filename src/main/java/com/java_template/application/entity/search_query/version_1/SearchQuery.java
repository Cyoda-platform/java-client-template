package com.java_template.application.entity.search_query.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SearchQuery Entity - Represents a book search query and its results
 * 
 * This entity stores search information including:
 * - Search parameters and filters
 * - Results metadata
 * - Performance metrics
 * - User interaction data
 */
@Data
public class SearchQuery implements CyodaEntity {
    public static final String ENTITY_NAME = SearchQuery.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String queryId;
    
    // Search parameters
    private String searchTerm;
    private String userId; // Reference to User entity
    private SearchFilters filters;
    
    // Search execution details
    private SearchExecution execution;
    
    // Results information
    private SearchResults results;
    
    // User interaction tracking
    private SearchInteraction interaction;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime executedAt;
    private LocalDateTime completedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return queryId != null && !queryId.trim().isEmpty() &&
               searchTerm != null && !searchTerm.trim().isEmpty();
    }

    /**
     * Nested class for search filters
     */
    @Data
    public static class SearchFilters {
        private List<String> genres;
        private List<String> authors;
        private Integer publicationYearStart;
        private Integer publicationYearEnd;
        private List<String> languages;
        private String sortBy; // "relevance", "title", "author", "year"
        private String sortOrder; // "asc", "desc"
        private Integer limit;
        private Integer offset;
    }

    /**
     * Nested class for search execution details
     */
    @Data
    public static class SearchExecution {
        private String status; // "pending", "executing", "completed", "failed"
        private Long executionTimeMs;
        private String dataSource; // "open_library", "cache", "database"
        private String apiEndpoint;
        private Integer retryCount;
        private String errorMessage;
        private Map<String, Object> debugInfo;
    }

    /**
     * Nested class for search results
     */
    @Data
    public static class SearchResults {
        private Integer totalResults;
        private Integer returnedResults;
        private List<String> bookIds; // References to Book entities
        private Boolean hasMoreResults;
        private String nextPageToken;
        private Double relevanceScore;
        private Map<String, Integer> genreDistribution;
        private Map<String, Integer> authorDistribution;
        private Map<Integer, Integer> yearDistribution;
    }

    /**
     * Nested class for user interaction tracking
     */
    @Data
    public static class SearchInteraction {
        private List<String> clickedBooks;
        private List<String> bookmarkedBooks;
        private Integer viewDurationSeconds;
        private Boolean refinedSearch;
        private String refinementType; // "filter_added", "filter_removed", "term_modified"
        private LocalDateTime lastInteractionAt;
        private Double satisfactionScore; // 0.0 to 1.0
    }
}

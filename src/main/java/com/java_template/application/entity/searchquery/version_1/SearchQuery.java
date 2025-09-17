package com.java_template.application.entity.searchquery.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.util.List;

/**
 * SearchQuery Entity - Represents a search query for finding HN items
 * 
 * Supports complex filtering and parent hierarchy joins for comprehensive search capabilities.
 */
@Data
public class SearchQuery implements CyodaEntity {
    public static final String ENTITY_NAME = SearchQuery.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String queryId;             // Unique identifier for the search query
    
    // Search criteria fields
    private String searchText;          // Text to search for in title, text, or author fields
    private String itemType;            // Filter by item type
    private String author;              // Filter by author username
    private Integer minScore;           // Minimum score threshold
    private Integer maxScore;           // Maximum score threshold
    private Long fromTime;              // Start time filter (Unix timestamp)
    private Long toTime;                // End time filter (Unix timestamp)
    private Boolean includeParentHierarchy; // Whether to include parent hierarchy in results
    private Integer maxDepth;           // Maximum depth for parent hierarchy traversal
    private String sortBy;              // Sort criteria: "score", "time", "relevance"
    private String sortOrder;           // Sort order: "asc", "desc"
    private Integer limit;              // Maximum number of results to return
    private Integer offset;             // Offset for pagination
    
    // Execution tracking fields
    private Long executionStartTime;
    private Long executionEndTime;
    private Long executionDuration;
    private Integer resultCount;
    private String errorMessage;
    
    // Results (populated after execution)
    private List<SearchResult> results;
    private List<SearchResult> intermediateResults;
    
    // Processing timestamps
    private Long validatedAt;

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
        if (queryId == null || queryId.trim().isEmpty()) {
            return false;
        }
        
        // Validate item type if present
        if (itemType != null && !isValidItemType(itemType)) {
            return false;
        }
        
        // Validate score range
        if (minScore != null && minScore < 0) {
            return false;
        }
        
        if (maxScore != null && minScore != null && maxScore < minScore) {
            return false;
        }
        
        // Validate time range
        if (fromTime != null && toTime != null && fromTime >= toTime) {
            return false;
        }
        
        // Validate pagination
        if (limit != null && limit <= 0) {
            return false;
        }
        
        if (offset != null && offset < 0) {
            return false;
        }
        
        // Validate hierarchy settings
        if (Boolean.TRUE.equals(includeParentHierarchy) && maxDepth != null && maxDepth <= 0) {
            return false;
        }
        
        // Validate sort parameters
        if (sortBy != null && !isValidSortBy(sortBy)) {
            return false;
        }
        
        if (sortOrder != null && !isValidSortOrder(sortOrder)) {
            return false;
        }
        
        return true;
    }
    
    private boolean isValidItemType(String type) {
        return "job".equals(type) || "story".equals(type) || "comment".equals(type) || 
               "poll".equals(type) || "pollopt".equals(type);
    }
    
    private boolean isValidSortBy(String sortBy) {
        return "score".equals(sortBy) || "time".equals(sortBy) || "relevance".equals(sortBy);
    }
    
    private boolean isValidSortOrder(String sortOrder) {
        return "asc".equals(sortOrder) || "desc".equals(sortOrder);
    }
    
    /**
     * Nested class for search results
     */
    @Data
    public static class SearchResult {
        private Long id;
        private String type;
        private String title;
        private String text;
        private String by;
        private Integer score;
        private Long time;
        private String url;
        private List<SearchResult> parentHierarchy;
    }
}

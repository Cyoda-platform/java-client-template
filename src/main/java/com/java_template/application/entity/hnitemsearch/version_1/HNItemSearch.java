package com.java_template.application.entity.hnitemsearch.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * HNItemSearch Entity - Manages search queries for Hacker News items
 * with support for hierarchical parent joins and complex filtering
 */
@Data
public class HNItemSearch implements CyodaEntity {
    public static final String ENTITY_NAME = HNItemSearch.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String searchId;           // Unique identifier for the search query (required)
    private String query;              // Search query string (required)
    private String searchType;         // Type of search - "text", "author", "type", "hierarchical" (required)
    
    // Optional fields
    private Map<String, Object> filters;        // JSON object containing search filters
    private Boolean includeParents;             // Boolean flag to include parent hierarchy in results
    private Integer maxResults;                 // Maximum number of results to return
    private Integer resultCount;                // Actual number of results found
    private LocalDateTime searchTimestamp;      // When the search was executed
    private Long executionTimeMs;               // Time taken to execute the search

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
        return searchId != null && !searchId.trim().isEmpty() &&
               query != null && !query.trim().isEmpty() &&
               searchType != null && !searchType.trim().isEmpty() &&
               (searchType.equals("text") || searchType.equals("author") || 
                searchType.equals("type") || searchType.equals("hierarchical"));
    }
}

package com.java_template.application.processor;

import com.java_template.application.entity.searchquery.version_1.SearchQuery;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SearchQueryExecutionProcessor - Executes search queries against indexed HN items
 * 
 * Builds search criteria and executes the search, optionally including parent hierarchy.
 */
@Component
public class SearchQueryExecutionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SearchQueryExecutionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SearchQueryExecutionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SearchQuery execution for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(SearchQuery.class)
                .validate(this::isValidEntityWithMetadata, "Invalid SearchQuery entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<SearchQuery> entityWithMetadata) {
        SearchQuery entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<SearchQuery> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<SearchQuery> context) {

        EntityWithMetadata<SearchQuery> entityWithMetadata = context.entityResponse();
        SearchQuery entity = entityWithMetadata.entity();

        logger.debug("Executing SearchQuery: {}", entity.getQueryId());

        // Build search criteria
        SearchCriteria searchCriteria = buildSearchCriteria(entity);

        // Execute search
        entity.setExecutionStartTime(System.currentTimeMillis());
        List<SearchQuery.SearchResult> searchResults = executeSearch(searchCriteria);

        // Apply parent hierarchy if requested
        if (Boolean.TRUE.equals(entity.getIncludeParentHierarchy())) {
            searchResults = enrichWithParentHierarchy(searchResults, entity.getMaxDepth());
        }

        // Store intermediate results
        entity.setIntermediateResults(searchResults);
        entity.setResultCount(searchResults.size());

        logger.info("SearchQuery {} executed successfully with {} results", entity.getQueryId(), searchResults.size());
        return entityWithMetadata;
    }

    private SearchCriteria buildSearchCriteria(SearchQuery query) {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchText(query.getSearchText());
        criteria.setItemType(query.getItemType());
        criteria.setAuthor(query.getAuthor());
        criteria.setMinScore(query.getMinScore());
        criteria.setMaxScore(query.getMaxScore());
        criteria.setFromTime(query.getFromTime());
        criteria.setToTime(query.getToTime());
        criteria.setSortBy(query.getSortBy());
        criteria.setSortOrder(query.getSortOrder());
        criteria.setLimit(query.getLimit());
        criteria.setOffset(query.getOffset());
        return criteria;
    }

    private List<SearchQuery.SearchResult> executeSearch(SearchCriteria criteria) {
        // Simulated search execution - in real implementation would query search index
        List<SearchQuery.SearchResult> results = new ArrayList<>();
        
        // Mock some results for demonstration
        SearchQuery.SearchResult result1 = new SearchQuery.SearchResult();
        result1.setId(8863L);
        result1.setType("story");
        result1.setTitle("My YC app: Dropbox - Throw away your USB drive");
        result1.setBy("dhouston");
        result1.setScore(111);
        result1.setTime(1175714200L);
        result1.setUrl("http://www.getdropbox.com/u/2/screencast.html");
        results.add(result1);

        logger.debug("Search executed with criteria: {}, found {} results", criteria, results.size());
        return results;
    }

    private List<SearchQuery.SearchResult> enrichWithParentHierarchy(List<SearchQuery.SearchResult> results, Integer maxDepth) {
        // Simulated parent hierarchy enrichment
        for (SearchQuery.SearchResult result : results) {
            result.setParentHierarchy(new ArrayList<>());
            logger.debug("Enriched result {} with parent hierarchy", result.getId());
        }
        return results;
    }

    /**
     * Internal class for search criteria
     */
    private static class SearchCriteria {
        private String searchText;
        private String itemType;
        private String author;
        private Integer minScore;
        private Integer maxScore;
        private Long fromTime;
        private Long toTime;
        private String sortBy;
        private String sortOrder;
        private Integer limit;
        private Integer offset;

        // Getters and setters
        public String getSearchText() { return searchText; }
        public void setSearchText(String searchText) { this.searchText = searchText; }
        public String getItemType() { return itemType; }
        public void setItemType(String itemType) { this.itemType = itemType; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public Integer getMinScore() { return minScore; }
        public void setMinScore(Integer minScore) { this.minScore = minScore; }
        public Integer getMaxScore() { return maxScore; }
        public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }
        public Long getFromTime() { return fromTime; }
        public void setFromTime(Long fromTime) { this.fromTime = fromTime; }
        public Long getToTime() { return toTime; }
        public void setToTime(Long toTime) { this.toTime = toTime; }
        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }
        public String getSortOrder() { return sortOrder; }
        public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
        public Integer getOffset() { return offset; }
        public void setOffset(Integer offset) { this.offset = offset; }
    }
}

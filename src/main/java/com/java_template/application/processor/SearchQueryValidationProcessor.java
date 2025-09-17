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

/**
 * SearchQueryValidationProcessor - Validates search query parameters
 * 
 * Ensures search query parameters are within acceptable limits and properly formatted.
 */
@Component
public class SearchQueryValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SearchQueryValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SearchQueryValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SearchQuery validation for request: {}", request.getId());

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
        return entity != null && technicalId != null;
    }

    private EntityWithMetadata<SearchQuery> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<SearchQuery> context) {

        EntityWithMetadata<SearchQuery> entityWithMetadata = context.entityResponse();
        SearchQuery entity = entityWithMetadata.entity();

        logger.debug("Validating SearchQuery: {}", entity.getQueryId());

        // Validate query ID
        if (entity.getQueryId() == null || entity.getQueryId().trim().isEmpty()) {
            throw new IllegalArgumentException("Query ID is required");
        }

        // Validate item type
        if (entity.getItemType() != null && !isValidItemType(entity.getItemType())) {
            throw new IllegalArgumentException("Invalid item type");
        }

        // Validate score range
        if (entity.getMinScore() != null && entity.getMinScore() < 0) {
            throw new IllegalArgumentException("Minimum score cannot be negative");
        }

        if (entity.getMaxScore() != null && entity.getMinScore() != null) {
            if (entity.getMaxScore() < entity.getMinScore()) {
                throw new IllegalArgumentException("Maximum score must be greater than minimum score");
            }
        }

        // Validate time range
        if (entity.getFromTime() != null && entity.getToTime() != null) {
            if (entity.getFromTime() >= entity.getToTime()) {
                throw new IllegalArgumentException("From time must be before to time");
            }
        }

        // Validate pagination
        if (entity.getLimit() != null && entity.getLimit() <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }

        if (entity.getOffset() != null && entity.getOffset() < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }

        // Validate hierarchy settings
        if (Boolean.TRUE.equals(entity.getIncludeParentHierarchy()) && 
            entity.getMaxDepth() != null && entity.getMaxDepth() <= 0) {
            throw new IllegalArgumentException("Max depth must be positive when including parent hierarchy");
        }

        // Validate sort parameters
        if (entity.getSortBy() != null && !isValidSortBy(entity.getSortBy())) {
            throw new IllegalArgumentException("Invalid sort by parameter");
        }

        if (entity.getSortOrder() != null && !isValidSortOrder(entity.getSortOrder())) {
            throw new IllegalArgumentException("Invalid sort order parameter");
        }

        // Set validation timestamp
        entity.setValidatedAt(System.currentTimeMillis());

        logger.info("SearchQuery {} validated successfully", entity.getQueryId());
        return entityWithMetadata;
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
}

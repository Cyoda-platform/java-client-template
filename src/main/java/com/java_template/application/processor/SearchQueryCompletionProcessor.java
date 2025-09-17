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
 * SearchQueryCompletionProcessor - Completes successful search execution
 * 
 * Finalizes search results and prepares them for consumption.
 */
@Component
public class SearchQueryCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SearchQueryCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SearchQueryCompletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SearchQuery completion for request: {}", request.getId());

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

        logger.debug("Completing SearchQuery: {}", entity.getQueryId());

        // Finalize results
        entity.setResults(entity.getIntermediateResults());
        entity.setExecutionEndTime(System.currentTimeMillis());
        
        if (entity.getExecutionStartTime() != null) {
            entity.setExecutionDuration(entity.getExecutionEndTime() - entity.getExecutionStartTime());
        }

        // Clear intermediate data
        entity.setIntermediateResults(null);

        // Log successful execution
        logSearchExecution(entity, "SUCCESS");

        logger.info("SearchQuery {} completed successfully", entity.getQueryId());
        return entityWithMetadata;
    }

    private void logSearchExecution(SearchQuery query, String status) {
        logger.info("Search execution logged - Query: {}, Status: {}, Results: {}, Duration: {}ms", 
                   query.getQueryId(), status, query.getResultCount(), query.getExecutionDuration());
    }
}

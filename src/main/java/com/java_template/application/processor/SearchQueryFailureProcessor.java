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
 * SearchQueryFailureProcessor - Handles failed search execution
 * 
 * Captures error information and cleans up after failed search execution.
 */
@Component
public class SearchQueryFailureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SearchQueryFailureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SearchQueryFailureProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SearchQuery failure for request: {}", request.getId());

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

        logger.debug("Handling SearchQuery failure: {}", entity.getQueryId());

        // Capture failure information
        entity.setExecutionEndTime(System.currentTimeMillis());
        
        if (entity.getExecutionStartTime() != null) {
            entity.setExecutionDuration(entity.getExecutionEndTime() - entity.getExecutionStartTime());
        }
        
        // Set error message (in real implementation, would capture actual exception)
        entity.setErrorMessage("Search execution failed");

        // Clear intermediate data
        entity.setIntermediateResults(null);
        entity.setResults(null);

        // Log failed execution
        logSearchExecution(entity, "FAILURE");

        logger.warn("SearchQuery {} failed", entity.getQueryId());
        return entityWithMetadata;
    }

    private void logSearchExecution(SearchQuery query, String status) {
        logger.warn("Search execution logged - Query: {}, Status: {}, Error: {}, Duration: {}ms", 
                   query.getQueryId(), status, query.getErrorMessage(), query.getExecutionDuration());
    }
}

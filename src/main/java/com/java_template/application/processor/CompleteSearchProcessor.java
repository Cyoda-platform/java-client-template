package com.java_template.application.processor;

import com.java_template.application.entity.hnitemsearch.version_1.HNItemSearch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * CompleteSearchProcessor - Finalizes search and sets completion timestamp
 */
@Component
public class CompleteSearchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompleteSearchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CompleteSearchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Completing search for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(HNItemSearch.class)
                .validate(this::isValidEntityWithMetadata, "Invalid HNItemSearch entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<HNItemSearch> entityWithMetadata) {
        HNItemSearch entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main completion logic processing method
     */
    private EntityWithMetadata<HNItemSearch> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItemSearch> context) {

        EntityWithMetadata<HNItemSearch> entityWithMetadata = context.entityResponse();
        HNItemSearch searchEntity = entityWithMetadata.entity();

        logger.debug("Completing search: {}", searchEntity.getSearchId());

        // Set completion timestamp
        searchEntity.setSearchTimestamp(LocalDateTime.now());

        // Log completion details
        logger.info("Search {} completed at {} with {} results in {}ms", 
                   searchEntity.getSearchId(), 
                   searchEntity.getSearchTimestamp(),
                   searchEntity.getResultCount() != null ? searchEntity.getResultCount() : 0,
                   searchEntity.getExecutionTimeMs() != null ? searchEntity.getExecutionTimeMs() : 0);

        return entityWithMetadata;
    }
}

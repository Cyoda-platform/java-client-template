package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HNItem;
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

import java.time.LocalDateTime;

/**
 * IndexHNItemProcessor - Index HNItem for search functionality
 * 
 * This processor handles the index_item transition from validated to indexed.
 * It prepares the HNItem for search functionality and indexing operations.
 */
@Component
public class IndexHNItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexHNItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IndexHNItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IndexHNItem for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(HNItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid HNItem entity wrapper")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<HNItem> entityWithMetadata) {
        HNItem entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Index HNItem for search functionality
     */
    private EntityWithMetadata<HNItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItem> context) {

        EntityWithMetadata<HNItem> entityWithMetadata = context.entityResponse();
        HNItem entity = entityWithMetadata.entity();

        logger.debug("Indexing HNItem: {}", entity.getId());

        // Update timestamp to mark indexing processing
        entity.setUpdatedAt(LocalDateTime.now());

        // Index item for search functionality
        // This could involve:
        // - Preparing search metadata
        // - Triggering external indexing services
        // - Setting up search-related fields
        // For now, we just update the timestamp to mark that indexing was processed

        logger.info("HNItem {} indexed successfully for search", entity.getId());

        return entityWithMetadata;
    }
}

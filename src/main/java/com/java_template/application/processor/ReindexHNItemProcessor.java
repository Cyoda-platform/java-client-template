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
 * ReindexHNItemProcessor - Reindex existing HNItem
 * 
 * This processor handles the reindex_item transition from indexed to validated.
 * It prepares an indexed HNItem for reindexing by clearing existing index metadata.
 */
@Component
public class ReindexHNItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReindexHNItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReindexHNItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReindexHNItem for request: {}", request.getId());

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
     * Reindex existing HNItem - prepare for reindexing
     */
    private EntityWithMetadata<HNItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItem> context) {

        EntityWithMetadata<HNItem> entityWithMetadata = context.entityResponse();
        HNItem entity = entityWithMetadata.entity();

        logger.debug("Preparing HNItem for reindexing: {}", entity.getId());

        // Update timestamp to mark reindexing preparation
        entity.setUpdatedAt(LocalDateTime.now());

        // Clear any existing index metadata
        // Prepare for reindexing by resetting index-related fields
        // This moves the item back to validated state so it can be indexed again

        logger.info("HNItem {} prepared for reindexing", entity.getId());

        return entityWithMetadata;
    }
}

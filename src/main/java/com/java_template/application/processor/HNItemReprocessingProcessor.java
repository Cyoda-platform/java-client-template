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

/**
 * HNItemReprocessingProcessor - Resets HN item for reprocessing
 * 
 * Clears derived fields and timestamps to prepare the item for reprocessing through the workflow.
 */
@Component
public class HNItemReprocessingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HNItemReprocessingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public HNItemReprocessingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem reprocessing for request: {}", request.getId());

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

    private boolean isValidEntityWithMetadata(EntityWithMetadata<HNItem> entityWithMetadata) {
        HNItem entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && technicalId != null;
    }

    private EntityWithMetadata<HNItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItem> context) {

        EntityWithMetadata<HNItem> entityWithMetadata = context.entityResponse();
        HNItem entity = entityWithMetadata.entity();

        logger.debug("Reprocessing HNItem: {}", entity.getId());

        // Clear derived fields
        entity.setDirectChildrenCount(null);
        entity.setDomain(null);
        entity.setUrlValid(null);
        entity.setTextLength(null);
        entity.setWordCount(null);
        entity.setAvailable(false);

        // Clear processing timestamps
        entity.setEnrichedAt(null);
        entity.setIndexedAt(null);
        entity.setProcessedAt(null);

        // Remove from search index (simulated)
        removeFromSearchIndex(entity.getId());

        logger.info("HNItem {} reset for reprocessing", entity.getId());
        return entityWithMetadata;
    }

    private void removeFromSearchIndex(Long itemId) {
        // Simulated search index removal
        logger.debug("Removing item {} from search index", itemId);
        // In real implementation, this would remove the document from the search index
    }
}

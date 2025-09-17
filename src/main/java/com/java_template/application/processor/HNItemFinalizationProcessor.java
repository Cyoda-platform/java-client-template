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
 * HNItemFinalizationProcessor - Finalizes HN item processing
 * 
 * Makes the HN item available for queries and handles final processing steps.
 */
@Component
public class HNItemFinalizationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HNItemFinalizationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public HNItemFinalizationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem finalization for request: {}", request.getId());

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
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<HNItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItem> context) {

        EntityWithMetadata<HNItem> entityWithMetadata = context.entityResponse();
        HNItem entity = entityWithMetadata.entity();

        logger.debug("Finalizing HNItem: {}", entity.getId());

        // Mark as available for queries
        entity.setAvailable(true);

        // Update global statistics (simulated)
        updateGlobalStatistics(entity.getType());

        // Trigger notifications if needed
        if ("story".equals(entity.getType()) && entity.getScore() != null && entity.getScore() > 100) {
            notifyHighScoreStory(entity);
        }

        // Set processing completion timestamp
        entity.setProcessedAt(System.currentTimeMillis());

        logger.info("HNItem {} finalized successfully", entity.getId());
        return entityWithMetadata;
    }

    private void updateGlobalStatistics(String itemType) {
        // Simulated global statistics update
        logger.debug("Updating global statistics for item type: {}", itemType);
        // In real implementation, this would update counters, metrics, etc.
    }

    private void notifyHighScoreStory(HNItem entity) {
        // Simulated notification for high-score stories
        logger.info("High-score story detected: {} with score {}", entity.getTitle(), entity.getScore());
        // In real implementation, this would send notifications, alerts, etc.
    }
}

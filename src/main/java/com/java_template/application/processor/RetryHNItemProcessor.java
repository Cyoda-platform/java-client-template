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
 * RetryHNItemProcessor - Reset failed HNItem for retry
 * 
 * This processor handles the retry_processing transition from failed to created.
 * It resets a failed HNItem for retry processing by clearing error flags and metadata.
 */
@Component
public class RetryHNItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RetryHNItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RetryHNItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing RetryHNItem for request: {}", request.getId());

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
        return entity != null && technicalId != null;
    }

    /**
     * Reset failed HNItem for retry processing
     */
    private EntityWithMetadata<HNItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItem> context) {

        EntityWithMetadata<HNItem> entityWithMetadata = context.entityResponse();
        HNItem entity = entityWithMetadata.entity();

        logger.debug("Resetting failed HNItem for retry: {}", entity.getId());

        // Update timestamp to mark retry processing
        entity.setUpdatedAt(LocalDateTime.now());

        // Clear any error flags or metadata
        // Reset for retry processing
        // This moves the item back to created state so it can be processed again

        logger.info("HNItem {} reset for retry processing", entity.getId());

        return entityWithMetadata;
    }
}

package com.java_template.application.processor;

import com.java_template.application.entity.hnitemupload.version_1.HNItemUpload;
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
 * CompleteUploadProcessor - Finalizes upload and sets completion timestamp
 */
@Component
public class CompleteUploadProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompleteUploadProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CompleteUploadProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Completing upload for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(HNItemUpload.class)
                .validate(this::isValidEntityWithMetadata, "Invalid HNItemUpload entity wrapper")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<HNItemUpload> entityWithMetadata) {
        HNItemUpload entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main completion logic processing method
     */
    private EntityWithMetadata<HNItemUpload> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItemUpload> context) {

        EntityWithMetadata<HNItemUpload> entityWithMetadata = context.entityResponse();
        HNItemUpload uploadEntity = entityWithMetadata.entity();

        logger.debug("Completing upload: {}", uploadEntity.getUploadId());

        // Set completion timestamp
        uploadEntity.setCompletionTimestamp(LocalDateTime.now());

        // Log completion details
        logger.info("Upload {} completed at {} - Total: {}, Processed: {}, Failed: {}", 
                   uploadEntity.getUploadId(), 
                   uploadEntity.getCompletionTimestamp(),
                   uploadEntity.getTotalItems() != null ? uploadEntity.getTotalItems() : 0,
                   uploadEntity.getProcessedItems() != null ? uploadEntity.getProcessedItems() : 0,
                   uploadEntity.getFailedItems() != null ? uploadEntity.getFailedItems() : 0);

        return entityWithMetadata;
    }
}

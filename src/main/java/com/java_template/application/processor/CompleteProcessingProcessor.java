package com.java_template.application.processor;

import com.java_template.application.entity.bulkupload.version_1.BulkUpload;
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
 * CompleteProcessingProcessor - Finalize processing and update counters
 * 
 * This processor handles the complete_processing transitions from processing to:
 * - completed (all items successful)
 * - completed_with_errors (some items failed)
 * - failed (all items failed)
 * 
 * It finalizes the processing status and sets completion timestamp.
 */
@Component
public class CompleteProcessingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompleteProcessingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CompleteProcessingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CompleteProcessing for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(BulkUpload.class)
                .validate(this::isValidEntityWithMetadata, "Invalid BulkUpload entity wrapper")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<BulkUpload> entityWithMetadata) {
        BulkUpload entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Finalize processing and update counters
     */
    private EntityWithMetadata<BulkUpload> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<BulkUpload> context) {

        EntityWithMetadata<BulkUpload> entityWithMetadata = context.entityResponse();
        BulkUpload entity = entityWithMetadata.entity();

        logger.debug("Completing processing for BulkUpload: {}", entity.getUploadId());

        // Update timestamps
        LocalDateTime now = LocalDateTime.now();
        entity.setUpdatedAt(now);
        entity.setCompletedAt(now);

        // Final counts are updated by individual item processors
        // This processor just sets completion timestamp
        
        // Log completion status
        int totalProcessed = (entity.getProcessedItems() != null ? entity.getProcessedItems() : 0) +
                           (entity.getFailedItems() != null ? entity.getFailedItems() : 0);
        
        logger.info("BulkUpload {} processing completed. Total: {}, Processed: {}, Failed: {}", 
                   entity.getUploadId(), 
                   entity.getTotalItems(),
                   entity.getProcessedItems(),
                   entity.getFailedItems());

        return entityWithMetadata;
    }
}

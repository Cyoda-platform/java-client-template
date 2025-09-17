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

/**
 * BulkUploadCompletionProcessor - Completes successful bulk upload processing
 * 
 * Finalizes the bulk upload when all items have been processed successfully.
 */
@Component
public class BulkUploadCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BulkUploadCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public BulkUploadCompletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BulkUpload completion for request: {}", request.getId());

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

    private boolean isValidEntityWithMetadata(EntityWithMetadata<BulkUpload> entityWithMetadata) {
        BulkUpload entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<BulkUpload> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<BulkUpload> context) {

        EntityWithMetadata<BulkUpload> entityWithMetadata = context.entityResponse();
        BulkUpload entity = entityWithMetadata.entity();

        logger.debug("Completing BulkUpload: {}", entity.getUploadId());

        entity.setProcessingEndTime(System.currentTimeMillis());
        
        if (entity.getProcessingStartTime() != null) {
            entity.setProcessingDuration(entity.getProcessingEndTime() - entity.getProcessingStartTime());
        }

        // Calculate success rate
        if (entity.getTotalItems() != null && entity.getTotalItems() > 0) {
            double successRate = (entity.getProcessedItems().doubleValue() / entity.getTotalItems().doubleValue()) * 100.0;
            entity.setSuccessRate(successRate);
        }

        // Log successful completion
        logBulkUpload(entity, "COMPLETED");

        logger.info("BulkUpload {} completed successfully", entity.getUploadId());
        return entityWithMetadata;
    }

    private void logBulkUpload(BulkUpload upload, String status) {
        logger.info("Bulk upload logged - Upload: {}, Status: {}, Total: {}, Processed: {}, Failed: {}, Duration: {}ms", 
                   upload.getUploadId(), status, upload.getTotalItems(), upload.getProcessedItems(), 
                   upload.getFailedItems(), upload.getProcessingDuration());
    }
}

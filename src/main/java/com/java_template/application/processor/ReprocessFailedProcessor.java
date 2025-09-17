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
import java.util.ArrayList;

/**
 * ReprocessFailedProcessor - Reprocess only failed items
 * 
 * This processor handles the reprocess_failed transition from completed_with_errors to processing.
 * It prepares a bulk upload with errors for reprocessing only the failed items.
 */
@Component
public class ReprocessFailedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReprocessFailedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReprocessFailedProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReprocessFailed for request: {}", request.getId());

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
     * Reprocess only failed items
     */
    private EntityWithMetadata<BulkUpload> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<BulkUpload> context) {

        EntityWithMetadata<BulkUpload> entityWithMetadata = context.entityResponse();
        BulkUpload entity = entityWithMetadata.entity();

        logger.debug("Preparing to reprocess failed items for BulkUpload: {}", entity.getUploadId());

        // Update timestamp
        entity.setUpdatedAt(LocalDateTime.now());

        // Clear completion timestamp for reprocessing
        entity.setCompletedAt(null);

        // Reset failed counters for reprocessing
        // Keep processedItems count as is (don't reprocess successful items)
        int previousFailedItems = entity.getFailedItems() != null ? entity.getFailedItems() : 0;
        entity.setFailedItems(0);

        // Clear error messages for fresh error tracking
        entity.setErrorMessages(new ArrayList<>());

        logger.info("BulkUpload {} prepared for reprocessing {} previously failed items", 
                   entity.getUploadId(), previousFailedItems);

        return entityWithMetadata;
    }
}

package com.java_template.application.processor;

import com.java_template.application.entity.bulkupload.version_1.BulkUpload;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
 * StartProcessingProcessor - Begin processing the uploaded file
 * 
 * This processor handles the start_processing transition from uploaded to processing.
 * It begins processing the uploaded file by parsing it and creating HNItem entities.
 */
@Component
public class StartProcessingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartProcessingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public StartProcessingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing StartProcessing for request: {}", request.getId());

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
     * Begin processing the uploaded file
     */
    private EntityWithMetadata<BulkUpload> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<BulkUpload> context) {

        EntityWithMetadata<BulkUpload> entityWithMetadata = context.entityResponse();
        BulkUpload entity = entityWithMetadata.entity();

        logger.debug("Starting processing for BulkUpload: {}", entity.getUploadId());

        // Update timestamp
        entity.setUpdatedAt(LocalDateTime.now());

        // Parse file and count total items
        // In a real implementation, this would:
        // 1. Read the JSON file from storage
        // 2. Parse the JSON array of HN items
        // 3. Set totalItems count
        // 4. Create HNItem entities asynchronously for each item
        
        // For now, we simulate this by setting a default total if not already set
        if (entity.getTotalItems() == null) {
            // This would be replaced with actual file parsing logic
            entity.setTotalItems(0); // Will be updated when actual items are processed
        }

        // Process each item asynchronously
        // In a real implementation, this would iterate through parsed items
        // and create HNItem entities with sourceType "BULK_UPLOAD"
        // For each item: processHNItem(item, entity.getUploadId())

        logger.info("BulkUpload {} processing started with {} total items", 
                   entity.getUploadId(), entity.getTotalItems());

        return entityWithMetadata;
    }

    /**
     * Process individual HN item from bulk upload
     * This would be called for each item in the JSON file
     */
    private void processHNItem(Object itemData, String uploadId) {
        try {
            // Create HNItem entity from parsed data
            HNItem hnItem = new HNItem();
            // Set fields from itemData
            hnItem.setSourceType("BULK_UPLOAD");
            
            // Create the entity using EntityService
            EntityWithMetadata<HNItem> createdItem = entityService.create(hnItem);
            
            logger.debug("Created HNItem {} from bulk upload {}", 
                        createdItem.entity().getId(), uploadId);
                        
        } catch (Exception e) {
            logger.error("Failed to process HN item from bulk upload {}: {}", uploadId, e.getMessage());
        }
    }
}

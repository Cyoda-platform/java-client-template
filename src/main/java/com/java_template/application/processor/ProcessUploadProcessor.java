package com.java_template.application.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.application.entity.hnitemupload.version_1.HNItemUpload;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ProcessUploadProcessor - Processes uploaded HN items and creates individual HNItem entities
 */
@Component
public class ProcessUploadProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessUploadProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ProcessUploadProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing upload for request: {}", request.getId());

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
     * Main upload processing logic processing method
     */
    private EntityWithMetadata<HNItemUpload> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItemUpload> context) {

        EntityWithMetadata<HNItemUpload> entityWithMetadata = context.entityResponse();
        HNItemUpload uploadEntity = entityWithMetadata.entity();

        logger.debug("Processing upload: {} of type: {}", uploadEntity.getUploadId(), uploadEntity.getUploadType());

        // Initialize processing counters
        uploadEntity.setProcessedItems(0);
        uploadEntity.setFailedItems(0);
        uploadEntity.setErrorMessages(new ArrayList<>());
        uploadEntity.setUploadTimestamp(LocalDateTime.now());

        try {
            // Parse upload data based on upload type
            List<Map<String, Object>> itemsData = parseUploadData(uploadEntity);
            uploadEntity.setTotalItems(itemsData.size());

            logger.info("Processing {} items for upload {}", itemsData.size(), uploadEntity.getUploadId());

            // Process each item
            for (Map<String, Object> itemData : itemsData) {
                try {
                    HNItem hnItem = createHNItemFromData(itemData);
                    
                    if (hnItem.isValid()) {
                        // Create the HNItem entity
                        EntityWithMetadata<HNItem> createdItem = entityService.create(hnItem);
                        uploadEntity.setProcessedItems(uploadEntity.getProcessedItems() + 1);
                        logger.debug("Successfully created HNItem with ID: {} (technical ID: {})", 
                                   hnItem.getId(), createdItem.metadata().getId());
                    } else {
                        uploadEntity.setFailedItems(uploadEntity.getFailedItems() + 1);
                        uploadEntity.getErrorMessages().add("Invalid HNItem data: ID=" + itemData.get("id"));
                        logger.warn("Invalid HNItem data for item: {}", itemData.get("id"));
                    }
                } catch (Exception e) {
                    uploadEntity.setFailedItems(uploadEntity.getFailedItems() + 1);
                    String errorMsg = "Failed to process item " + itemData.get("id") + ": " + e.getMessage();
                    uploadEntity.getErrorMessages().add(errorMsg);
                    logger.error("Error processing item {}: {}", itemData.get("id"), e.getMessage());
                }
            }

            logger.info("Upload {} processing completed: {} processed, {} failed", 
                       uploadEntity.getUploadId(), uploadEntity.getProcessedItems(), uploadEntity.getFailedItems());

        } catch (Exception e) {
            logger.error("Error processing upload {}: {}", uploadEntity.getUploadId(), e.getMessage());
            uploadEntity.setFailedItems(uploadEntity.getTotalItems() != null ? uploadEntity.getTotalItems() : 1);
            uploadEntity.setProcessedItems(0);
            uploadEntity.getErrorMessages().add("Upload processing failed: " + e.getMessage());
            throw new RuntimeException("Upload processing failed: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Parse upload data based on upload type
     */
    private List<Map<String, Object>> parseUploadData(HNItemUpload uploadEntity) {
        List<Map<String, Object>> itemsData = new ArrayList<>();
        
        // For this implementation, we'll create placeholder data
        // In a real system, you would:
        // 1. For "single" type: parse single item from request
        // 2. For "array" type: parse array of items from request
        // 3. For "file" type: read and parse JSON file
        
        switch (uploadEntity.getUploadType().toLowerCase()) {
            case "single":
                // Placeholder: Create a single test item
                Map<String, Object> singleItem = createPlaceholderItem(1);
                itemsData.add(singleItem);
                break;
                
            case "array":
                // Placeholder: Create multiple test items
                for (int i = 1; i <= 3; i++) {
                    itemsData.add(createPlaceholderItem(i));
                }
                break;
                
            case "file":
                // Placeholder: Create items from "file"
                for (int i = 1; i <= 5; i++) {
                    itemsData.add(createPlaceholderItem(i));
                }
                break;
                
            default:
                throw new IllegalArgumentException("Unknown upload type: " + uploadEntity.getUploadType());
        }
        
        return itemsData;
    }

    /**
     * Create placeholder item data for testing
     */
    private Map<String, Object> createPlaceholderItem(int index) {
        Map<String, Object> item = Map.of(
            "id", 1000 + index,
            "type", "story",
            "by", "testuser" + index,
            "time", System.currentTimeMillis() / 1000,
            "title", "Test Story " + index,
            "url", "https://example.com/story" + index,
            "score", 10 + index
        );
        return item;
    }

    /**
     * Create HNItem entity from raw data
     */
    private HNItem createHNItemFromData(Map<String, Object> itemData) {
        HNItem hnItem = new HNItem();
        
        // Map required fields
        if (itemData.get("id") != null) {
            hnItem.setId(((Number) itemData.get("id")).intValue());
        }
        if (itemData.get("type") != null) {
            hnItem.setType((String) itemData.get("type"));
        }
        
        // Map optional fields
        if (itemData.get("by") != null) {
            hnItem.setBy((String) itemData.get("by"));
        }
        if (itemData.get("time") != null) {
            hnItem.setTime(((Number) itemData.get("time")).longValue());
        }
        if (itemData.get("title") != null) {
            hnItem.setTitle((String) itemData.get("title"));
        }
        if (itemData.get("text") != null) {
            hnItem.setText((String) itemData.get("text"));
        }
        if (itemData.get("url") != null) {
            hnItem.setUrl((String) itemData.get("url"));
        }
        if (itemData.get("score") != null) {
            hnItem.setScore(((Number) itemData.get("score")).intValue());
        }
        if (itemData.get("parent") != null) {
            hnItem.setParent(((Number) itemData.get("parent")).intValue());
        }
        if (itemData.get("descendants") != null) {
            hnItem.setDescendants(((Number) itemData.get("descendants")).intValue());
        }
        if (itemData.get("poll") != null) {
            hnItem.setPoll(((Number) itemData.get("poll")).intValue());
        }
        if (itemData.get("deleted") != null) {
            hnItem.setDeleted((Boolean) itemData.get("deleted"));
        }
        if (itemData.get("dead") != null) {
            hnItem.setDead((Boolean) itemData.get("dead"));
        }
        
        // Handle arrays
        if (itemData.get("kids") != null) {
            @SuppressWarnings("unchecked")
            List<Integer> kids = (List<Integer>) itemData.get("kids");
            hnItem.setKids(kids);
        }
        if (itemData.get("parts") != null) {
            @SuppressWarnings("unchecked")
            List<Integer> parts = (List<Integer>) itemData.get("parts");
            hnItem.setParts(parts);
        }
        
        return hnItem;
    }
}

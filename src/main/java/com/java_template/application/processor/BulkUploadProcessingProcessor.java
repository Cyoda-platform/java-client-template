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

import java.util.ArrayList;
import java.util.List;

/**
 * BulkUploadProcessingProcessor - Processes individual HN items from bulk upload
 * 
 * Iterates through the uploaded file and creates individual HNItem entities.
 */
@Component
public class BulkUploadProcessingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BulkUploadProcessingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public BulkUploadProcessingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BulkUpload processing for request: {}", request.getId());

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

        logger.debug("Processing BulkUpload: {}", entity.getUploadId());

        entity.setProcessingStartTime(System.currentTimeMillis());
        entity.setProcessedItems(0);
        entity.setFailedItems(0);
        entity.setErrorMessages(new ArrayList<>());

        MockFile file = getUploadedFile(entity.getFileName());
        MockJsonContent jsonContent = parseJsonFile(file);

        for (int i = 0; i < jsonContent.size(); i++) {
            MockJsonItem item = jsonContent.get(i);
            try {
                // Create HNItem entity
                HNItem hnItem = createHNItemFromJson(item);

                // Save and trigger HNItem workflow (transition: null - entity will start in INITIAL state)
                entityService.create(hnItem);

                entity.setProcessedItems(entity.getProcessedItems() + 1);

            } catch (Exception e) {
                entity.setFailedItems(entity.getFailedItems() + 1);
                entity.getErrorMessages().add("Item " + item.getId() + ": " + e.getMessage());
                logger.warn("Failed to process item {}: {}", item.getId(), e.getMessage());
            }
        }

        logger.info("BulkUpload {} processing completed - Processed: {}, Failed: {}", 
                   entity.getUploadId(), entity.getProcessedItems(), entity.getFailedItems());
        return entityWithMetadata;
    }

    private MockFile getUploadedFile(String fileName) {
        // Simulated file retrieval
        return new MockFile(fileName);
    }

    private MockJsonContent parseJsonFile(MockFile file) {
        // Simulated JSON parsing
        return new MockJsonContent(100); // Mock 100 items
    }

    private HNItem createHNItemFromJson(MockJsonItem item) {
        HNItem hnItem = new HNItem();
        hnItem.setId(item.getId());
        hnItem.setType(item.getType());
        hnItem.setBy(item.getBy());
        hnItem.setTime(item.getTime());
        hnItem.setTitle(item.getTitle());
        hnItem.setText(item.getText());
        hnItem.setUrl(item.getUrl());
        hnItem.setScore(item.getScore());
        return hnItem;
    }

    // Mock classes for simulation
    private static class MockFile {
        private final String name;

        public MockFile(String name) {
            this.name = name;
        }
    }

    private static class MockJsonContent {
        private final int itemCount;

        public MockJsonContent(int itemCount) {
            this.itemCount = itemCount;
        }

        public int size() { return itemCount; }
        public MockJsonItem get(int index) { 
            return new MockJsonItem((long)(index + 1), "story", "user" + index, 
                                  System.currentTimeMillis(), "Title " + index, 
                                  "Text " + index, "http://example.com/" + index, 10 + index); 
        }
    }

    private static class MockJsonItem {
        private final Long id;
        private final String type;
        private final String by;
        private final Long time;
        private final String title;
        private final String text;
        private final String url;
        private final Integer score;

        public MockJsonItem(Long id, String type, String by, Long time, String title, String text, String url, Integer score) {
            this.id = id;
            this.type = type;
            this.by = by;
            this.time = time;
            this.title = title;
            this.text = text;
            this.url = url;
            this.score = score;
        }

        public Long getId() { return id; }
        public String getType() { return type; }
        public String getBy() { return by; }
        public Long getTime() { return time; }
        public String getTitle() { return title; }
        public String getText() { return text; }
        public String getUrl() { return url; }
        public Integer getScore() { return score; }
    }
}

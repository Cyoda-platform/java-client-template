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
 * BulkUploadValidationProcessor - Validates uploaded file format and structure
 * 
 * Ensures the uploaded file exists, is readable, and contains valid JSON structure.
 */
@Component
public class BulkUploadValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BulkUploadValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100 MB

    public BulkUploadValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BulkUpload validation for request: {}", request.getId());

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
        return entity != null && technicalId != null;
    }

    private EntityWithMetadata<BulkUpload> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<BulkUpload> context) {

        EntityWithMetadata<BulkUpload> entityWithMetadata = context.entityResponse();
        BulkUpload entity = entityWithMetadata.entity();

        logger.debug("Validating BulkUpload: {}", entity.getUploadId());

        // Validate file exists and is readable (simulated)
        MockFile file = getUploadedFile(entity.getFileName());
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("File not found or not readable");
        }

        // Validate file size
        if (file.size() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum limit");
        }

        // Validate JSON format (simulated)
        try {
            MockJsonContent jsonContent = parseJsonFile(file);
            if (!jsonContent.isArray()) {
                throw new IllegalArgumentException("File must contain a JSON array of HN items");
            }

            entity.setTotalItems(jsonContent.size());

            // Basic validation of first few items
            int itemsToValidate = Math.min(5, jsonContent.size());
            for (int i = 0; i < itemsToValidate; i++) {
                MockJsonItem item = jsonContent.get(i);
                validateBasicHNItemStructure(item);
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage());
        }

        entity.setValidatedAt(System.currentTimeMillis());

        logger.info("BulkUpload {} validated successfully", entity.getUploadId());
        return entityWithMetadata;
    }

    private MockFile getUploadedFile(String fileName) {
        // Simulated file retrieval
        return new MockFile(fileName, 1024 * 1024); // 1MB mock file
    }

    private MockJsonContent parseJsonFile(MockFile file) {
        // Simulated JSON parsing
        return new MockJsonContent(100); // Mock 100 items
    }

    private void validateBasicHNItemStructure(MockJsonItem item) {
        // Simulated basic validation
        if (item.getId() == null) {
            throw new IllegalArgumentException("Item missing required 'id' field");
        }
        if (item.getType() == null) {
            throw new IllegalArgumentException("Item missing required 'type' field");
        }
    }

    // Mock classes for simulation
    private static class MockFile {
        private final String name;
        private final long size;

        public MockFile(String name, long size) {
            this.name = name;
            this.size = size;
        }

        public boolean exists() { return true; }
        public long size() { return size; }
    }

    private static class MockJsonContent {
        private final int itemCount;

        public MockJsonContent(int itemCount) {
            this.itemCount = itemCount;
        }

        public boolean isArray() { return true; }
        public int size() { return itemCount; }
        public MockJsonItem get(int index) { return new MockJsonItem(index + 1L, "story"); }
    }

    private static class MockJsonItem {
        private final Long id;
        private final String type;

        public MockJsonItem(Long id, String type) {
            this.id = id;
            this.type = type;
        }

        public Long getId() { return id; }
        public String getType() { return type; }
    }
}

package com.java_template.application.processor;

import com.java_template.application.entity.document.version_1.Document;
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

/**
 * DocumentValidationProcessor - Handles document validation logic
 * Processes document validation and integrity checks
 */
@Component
public class DocumentValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DocumentValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Document validation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Document.class)
                .validate(this::isValidEntityWithMetadata, "Invalid document entity wrapper")
                .map(this::processDocumentValidationLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Document> entityWithMetadata) {
        Document entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for document validation
     */
    private EntityWithMetadata<Document> processDocumentValidationLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Document> context) {

        EntityWithMetadata<Document> entityWithMetadata = context.entityResponse();
        Document document = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing document validation: {} in state: {}", document.getFileName(), currentState);

        // Perform validation checks
        boolean isValid = performValidationChecks(document);

        if (isValid) {
            logger.info("Document {} validation successful", document.getFileName());
        } else {
            logger.warn("Document {} validation failed", document.getFileName());
        }

        return entityWithMetadata;
    }

    /**
     * Perform comprehensive validation checks on the document
     */
    private boolean performValidationChecks(Document document) {
        boolean isValid = true;

        // Check file name validity
        if (!isValidFileName(document.getFileName())) {
            logger.warn("Invalid file name: {}", document.getFileName());
            isValid = false;
        }

        // Check file type validity
        if (!isValidFileType(document.getFileType())) {
            logger.warn("Invalid or unsupported file type: {}", document.getFileType());
            isValid = false;
        }

        // Check file size validity
        if (!isValidFileSize(document.getFileSize(), document.getFileType())) {
            logger.warn("Invalid file size {} for type {}", document.getFileSize(), document.getFileType());
            isValid = false;
        }

        // Check checksum validity (simplified check)
        if (!isValidChecksum(document.getChecksum())) {
            logger.warn("Invalid checksum format: {}", document.getChecksum());
            isValid = false;
        }

        // Check version validity
        if (!isValidVersion(document.getVersion())) {
            logger.warn("Invalid version number: {}", document.getVersion());
            isValid = false;
        }

        return isValid;
    }

    /**
     * Validate file name
     */
    private boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        // Check for invalid characters
        String invalidChars = "<>:\"/\\|?*";
        for (char c : invalidChars.toCharArray()) {
            if (fileName.indexOf(c) >= 0) {
                return false;
            }
        }
        
        // Check length
        return fileName.length() <= 255;
    }

    /**
     * Validate file type (MIME type)
     */
    private boolean isValidFileType(String fileType) {
        if (fileType == null || !fileType.contains("/")) {
            return false;
        }
        
        // List of allowed file types
        String[] allowedTypes = {
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "text/csv",
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/bmp"
        };
        
        for (String allowedType : allowedTypes) {
            if (fileType.equals(allowedType)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Validate file size based on type
     */
    private boolean isValidFileSize(Long fileSize, String fileType) {
        if (fileSize == null || fileSize <= 0) {
            return false;
        }
        
        // Define size limits based on file type (in bytes)
        long maxSize = 104857600L; // Default 100MB
        
        if (fileType.startsWith("image/")) {
            maxSize = 10485760L; // 10MB for images
        } else if (fileType.equals("application/pdf")) {
            maxSize = 52428800L; // 50MB for PDFs
        } else if (fileType.startsWith("text/")) {
            maxSize = 1048576L; // 1MB for text files
        }
        
        return fileSize <= maxSize;
    }

    /**
     * Validate checksum format
     */
    private boolean isValidChecksum(String checksum) {
        if (checksum == null || checksum.trim().isEmpty()) {
            return false;
        }
        
        // Simple validation - should be alphanumeric and reasonable length
        return checksum.matches("^[a-fA-F0-9_]+$") && checksum.length() >= 8 && checksum.length() <= 64;
    }

    /**
     * Validate version number
     */
    private boolean isValidVersion(Integer version) {
        return version != null && version > 0 && version <= 1000; // Reasonable upper limit
    }
}

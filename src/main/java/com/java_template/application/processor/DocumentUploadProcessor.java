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

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * DocumentUploadProcessor - Handles document upload logic
 * Processes document uploads and sets initial values
 */
@Component
public class DocumentUploadProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentUploadProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DocumentUploadProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Document upload for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Document.class)
                .validate(this::isValidEntityWithMetadata, "Invalid document entity wrapper")
                .map(this::processDocumentUploadLogic)
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
     * Main business logic for document upload
     */
    private EntityWithMetadata<Document> processDocumentUploadLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Document> context) {

        EntityWithMetadata<Document> entityWithMetadata = context.entityResponse();
        Document document = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing document upload: {} in state: {}", document.getFileName(), currentState);

        // Set upload timestamp if not already set
        if (document.getUploadDate() == null) {
            document.setUploadDate(LocalDateTime.now());
        }

        // Set default version if not specified
        if (document.getVersion() == null) {
            document.setVersion(1); // Default to version 1
        }

        // Generate checksum if not provided (simplified example)
        if (document.getChecksum() == null || document.getChecksum().trim().isEmpty()) {
            document.setChecksum(generateSimpleChecksum(document));
        }

        // Set default file path if not provided
        if (document.getFilePath() == null || document.getFilePath().trim().isEmpty()) {
            document.setFilePath(generateFilePath(document));
        }

        // Validate file size limits based on file type
        validateFileSizeByType(document);

        logger.info("Document {} uploaded successfully by {} for submission {}", 
                   document.getFileName(), document.getUploadedBy(), document.getSubmissionId());

        return entityWithMetadata;
    }

    /**
     * Generate a simple checksum for the document (simplified example)
     */
    private String generateSimpleChecksum(Document document) {
        try {
            String content = document.getFileName() + document.getFileSize() + document.getUploadDate();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16); // First 16 characters
        } catch (Exception e) {
            logger.warn("Error generating checksum, using fallback", e);
            return "checksum_" + System.currentTimeMillis();
        }
    }

    /**
     * Generate file path for storage
     */
    private String generateFilePath(Document document) {
        return String.format("/documents/%s/v%d/%s", 
                           document.getSubmissionId(), 
                           document.getVersion(), 
                           document.getFileName());
    }

    /**
     * Validate file size based on file type
     */
    private void validateFileSizeByType(Document document) {
        String fileType = document.getFileType().toLowerCase();
        Long fileSize = document.getFileSize();
        
        // Define size limits based on file type (in bytes)
        long maxSize = 104857600L; // Default 100MB
        
        if (fileType.startsWith("image/")) {
            maxSize = 10485760L; // 10MB for images
        } else if (fileType.equals("application/pdf")) {
            maxSize = 52428800L; // 50MB for PDFs
        } else if (fileType.startsWith("text/")) {
            maxSize = 1048576L; // 1MB for text files
        }
        
        if (fileSize > maxSize) {
            logger.warn("File size {} exceeds limit {} for type {}", fileSize, maxSize, fileType);
        }
    }
}

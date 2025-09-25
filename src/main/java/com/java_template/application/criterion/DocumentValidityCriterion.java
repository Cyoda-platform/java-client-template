package com.java_template.application.criterion;

import com.java_template.application.entity.document.version_1.Document;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DocumentValidityCriterion - Validates document content and integrity
 * Checks if document meets validity requirements for validation
 */
@Component
public class DocumentValidityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DocumentValidityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Document validity criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Document.class, this::validateDocumentValidity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for document validity
     */
    private EvaluationOutcome validateDocumentValidity(CriterionSerializer.CriterionEntityEvaluationContext<Document> context) {
        Document document = context.entityWithMetadata().entity();

        // Check if document is null (structural validation)
        if (document == null) {
            logger.warn("Document is null");
            return EvaluationOutcome.fail("Document is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check basic entity validity
        if (!document.isValid()) {
            logger.warn("Document is not valid: {}", document.getFileName());
            return EvaluationOutcome.fail("Document is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check file name validity
        if (!isValidFileName(document.getFileName())) {
            logger.warn("Invalid file name: {}", document.getFileName());
            return EvaluationOutcome.fail("Invalid file name format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check file type validity
        if (!isValidFileType(document.getFileType())) {
            logger.warn("Invalid or unsupported file type: {}", document.getFileType());
            return EvaluationOutcome.fail("Unsupported file type", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check file size validity based on type
        if (!isValidFileSizeForType(document.getFileSize(), document.getFileType())) {
            logger.warn("Invalid file size {} for type {}", document.getFileSize(), document.getFileType());
            return EvaluationOutcome.fail("File size exceeds limit for this file type", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check checksum validity
        if (!isValidChecksum(document.getChecksum())) {
            logger.warn("Invalid checksum format: {}", document.getChecksum());
            return EvaluationOutcome.fail("Invalid checksum format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check version validity
        if (!isValidVersion(document.getVersion())) {
            logger.warn("Invalid version number: {}", document.getVersion());
            return EvaluationOutcome.fail("Invalid version number", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check file path validity
        if (!isValidFilePath(document.getFilePath())) {
            logger.warn("Invalid file path: {}", document.getFilePath());
            return EvaluationOutcome.fail("Invalid file path format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
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
        
        // Check length and extension
        return fileName.length() <= 255 && fileName.contains(".");
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
    private boolean isValidFileSizeForType(Long fileSize, String fileType) {
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
        
        // Should be alphanumeric and reasonable length
        return checksum.matches("^[a-fA-F0-9_]+$") && checksum.length() >= 8 && checksum.length() <= 64;
    }

    /**
     * Validate version number
     */
    private boolean isValidVersion(Integer version) {
        return version != null && version > 0 && version <= 1000; // Reasonable upper limit
    }

    /**
     * Validate file path format
     */
    private boolean isValidFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        
        // Should start with / and contain reasonable path structure
        return filePath.startsWith("/") && filePath.length() <= 500 && !filePath.contains("..");
    }
}

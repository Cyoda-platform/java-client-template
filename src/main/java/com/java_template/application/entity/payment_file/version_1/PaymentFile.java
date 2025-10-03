package com.java_template.application.entity.payment_file.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: This file contains the PaymentFile entity representing batch import containers
 * for payment files like bank statements that yield individual Payment entities.
 */
@Data
@NoArgsConstructor
public class PaymentFile implements CyodaEntity {
    public static final String ENTITY_NAME = PaymentFile.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    @NonNull
    private String paymentFileId;
    
    // File metadata
    private String fileName;
    private String fileType; // CSV, JSON, XML, etc.
    private Long fileSize;
    private String fileHash;
    private String originalFileName;
    
    // Processing information
    private Integer totalRecords;
    private Integer validRecords;
    private Integer invalidRecords;
    private Integer processedRecords;
    
    // Validation results
    private List<FileValidationError> validationErrors;
    private List<String> createdPaymentIds;
    
    // Processing metadata
    private LocalDateTime receivedAt;
    private LocalDateTime validationStartedAt;
    private LocalDateTime validationCompletedAt;
    private LocalDateTime importStartedAt;
    private LocalDateTime importCompletedAt;
    
    // Status and metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return paymentFileId != null && !paymentFileId.trim().isEmpty() &&
               fileName != null && !fileName.trim().isEmpty() &&
               fileType != null && !fileType.trim().isEmpty();
    }

    /**
     * Nested class for file validation errors
     */
    @Data
    public static class FileValidationError {
        private Integer lineNumber;
        private String errorCode;
        private String errorMessage;
        private String fieldName;
        private String fieldValue;
    }
}

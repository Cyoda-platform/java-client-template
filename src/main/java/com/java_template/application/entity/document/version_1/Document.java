package com.java_template.application.entity.document.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Document Entity for Research & Clinical Trial Management platform
 * Represents documents attached to submissions with version control and audit trail
 */
@Data
public class Document implements CyodaEntity {
    public static final String ENTITY_NAME = Document.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core business fields
    private String fileName;
    private String fileType; // MIME type
    private Long fileSize; // File size in bytes
    private String submissionId; // UUID of associated submission
    private Integer version; // Document version number
    private String uploadedBy; // Email of user who uploaded the document
    private LocalDateTime uploadDate;
    private String checksum; // File integrity checksum
    private String filePath; // Storage path reference

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return fileName != null && !fileName.trim().isEmpty() &&
               fileType != null && !fileType.trim().isEmpty() &&
               fileSize != null && fileSize > 0 &&
               submissionId != null && !submissionId.trim().isEmpty() &&
               version != null && version > 0 &&
               uploadedBy != null && !uploadedBy.trim().isEmpty() &&
               uploadDate != null &&
               checksum != null && !checksum.trim().isEmpty() &&
               filePath != null && !filePath.trim().isEmpty() &&
               isValidEmail(uploadedBy) &&
               isValidFileType(fileType) &&
               isValidFileSize(fileSize);
    }

    /**
     * Basic email validation
     */
    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }

    /**
     * Validates file type (basic MIME type validation)
     */
    private boolean isValidFileType(String fileType) {
        return fileType != null && fileType.contains("/");
    }

    /**
     * Validates file size (must be positive and reasonable - max 100MB)
     */
    private boolean isValidFileSize(Long fileSize) {
        return fileSize != null && fileSize > 0 && fileSize <= 104857600L; // 100MB max
    }
}

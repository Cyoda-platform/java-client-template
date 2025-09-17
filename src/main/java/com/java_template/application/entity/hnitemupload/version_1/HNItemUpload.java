package com.java_template.application.entity.hnitemupload.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HNItemUpload Entity - Manages bulk upload operations for Hacker News items
 * Handles single items, arrays of items, and JSON file uploads
 */
@Data
public class HNItemUpload implements CyodaEntity {
    public static final String ENTITY_NAME = HNItemUpload.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String uploadId;           // Unique identifier for the upload operation (required)
    private String uploadType;         // Type of upload - "single", "array", or "file" (required)
    
    // Optional fields
    private String fileName;           // Name of uploaded file (for file uploads)
    private Integer totalItems;        // Total number of items to process
    private Integer processedItems;    // Number of items successfully processed
    private Integer failedItems;       // Number of items that failed processing
    private List<String> errorMessages; // Array of error messages for failed items
    private LocalDateTime uploadTimestamp;      // When the upload was initiated
    private LocalDateTime completionTimestamp;  // When the upload was completed

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
        return uploadId != null && !uploadId.trim().isEmpty() &&
               uploadType != null && !uploadType.trim().isEmpty() &&
               (uploadType.equals("single") || uploadType.equals("array") || uploadType.equals("file"));
    }
}

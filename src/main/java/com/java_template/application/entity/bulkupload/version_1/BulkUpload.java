package com.java_template.application.entity.bulkupload.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BulkUpload Entity - Manages bulk upload operations of HN items from JSON files
 * 
 * This entity tracks the upload process, status, and provides metadata about the bulk operation.
 * It manages the lifecycle of bulk upload operations from file upload through processing completion.
 */
@Data
public class BulkUpload implements CyodaEntity {
    public static final String ENTITY_NAME = BulkUpload.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required Fields
    private String uploadId;            // Unique identifier for the bulk upload operation
    private String fileName;            // Name of the uploaded JSON file
    private LocalDateTime uploadedAt;   // When the upload was initiated

    // Optional Fields
    private Long fileSize;              // Size of the uploaded file in bytes
    private Integer totalItems;         // Total number of items in the file
    private Integer processedItems;     // Number of items successfully processed
    private Integer failedItems;        // Number of items that failed to process
    private List<String> errorMessages; // List of error messages encountered during processing
    private String uploadedBy;          // User or system that initiated the upload
    private LocalDateTime completedAt;  // When the upload processing was completed

    // Technical Fields
    private LocalDateTime createdAt;    // When the entity was created in our system
    private LocalDateTime updatedAt;    // When the entity was last updated in our system

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
        if (uploadId == null || uploadId.trim().isEmpty()) {
            return false;
        }
        
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        if (uploadedAt == null) {
            return false;
        }
        
        // Validate counters are non-negative
        if (processedItems != null && processedItems < 0) {
            return false;
        }
        
        if (failedItems != null && failedItems < 0) {
            return false;
        }
        
        if (totalItems != null && totalItems < 0) {
            return false;
        }
        
        // Validate that processed + failed doesn't exceed total
        if (totalItems != null && processedItems != null && failedItems != null) {
            if (processedItems + failedItems > totalItems) {
                return false;
            }
        }
        
        return true;
    }
}

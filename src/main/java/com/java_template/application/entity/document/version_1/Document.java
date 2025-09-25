package com.java_template.application.entity.document.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Document Entity for Digital Document Management (eTMF-style)
 * 
 * Represents a document with versioning, audit trail, and metadata tracking.
 * Based on functional requirements from PRD and API Data Contracts.
 */
@Data
public class Document implements CyodaEntity {
    public static final String ENTITY_NAME = Document.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String documentId;
    
    // Core document fields
    private String name;
    private String type; // "protocol", "IB", "ICF", "CV", "budget", "insurance", "ethics_approval", "other"
    private String versionLabel; // e.g., "v1.0"
    private String status; // "draft", "final", "superseded", "withdrawn"
    
    // Document metadata
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String checksumSha256;
    private Long fileSizeBytes;
    private String contentType; // RFC 6838 MIME type
    private List<String> classifications; // "PHI", "PII", "CONFIDENTIAL", "PUBLIC"
    private String retentionCategory;
    
    // File storage reference
    private String fileReference; // Opaque storage URI
    
    // Versioning
    private String parentDocumentId; // Reference to original document for versions
    private Integer versionNumber;
    private String changeNote;
    
    // Relationships
    private String submissionId; // Link to submission if applicable
    private String studyId; // Link to study if applicable
    
    // Audit fields
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
        return documentId != null && !documentId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               type != null && !type.trim().isEmpty() &&
               versionLabel != null && !versionLabel.trim().isEmpty() &&
               status != null && !status.trim().isEmpty();
    }
}

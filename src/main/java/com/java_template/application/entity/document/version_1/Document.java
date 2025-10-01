package com.java_template.application.entity.document.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Document entity for digital document management (eTMF-style)
 * Supports versioning, audit trails, and controlled access
 */
@Data
public class Document implements CyodaEntity {
    public static final String ENTITY_NAME = Document.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String documentId;
    
    // Core document metadata
    private String name;
    private String type; // protocol, IB, ICF, CV, budget, insurance, ethics_approval, other
    private String versionLabel;
    private String status; // draft, final, superseded, withdrawn
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    
    // File metadata
    private String checksumSha256;
    private Long fileSizeBytes;
    private String contentType;
    private List<String> classifications; // PHI, PII, CONFIDENTIAL, PUBLIC
    private String retentionCategory;
    
    // Audit fields
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Version tracking
    private List<DocumentVersion> versions;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return documentId != null && !documentId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               type != null && !type.trim().isEmpty();
    }

    /**
     * Nested class for document version tracking
     */
    @Data
    public static class DocumentVersion {
        private String versionId;
        private String versionLabel;
        private LocalDateTime createdAt;
        private String createdBy;
        private String changeNote;
        private String fileRef; // opaque storage URI
    }
}

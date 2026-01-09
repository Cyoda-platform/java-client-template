package com.example.application.entity.document_evidence.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DocumentEvidence Entity - Compliance Management Platform
 *
 * Represents evidence documents uploaded as part of compliance case investigations.
 * Tracks document metadata, storage information, and compliance-related tags.
 *
 * This entity implements CyodaEntity and follows the standard pattern for:
 * - Document identification and case association
 * - File metadata (name, type, size)
 * - Storage and integrity verification (path, checksum)
 * - Audit trail (uploader, upload timestamp)
 * - Compliance classification (tags, metadata)
 */
@Data
public class DocumentEvidence implements CyodaEntity {
    public static final String ENTITY_NAME = "DocumentEvidence";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String id;

    // Required core business fields
    private String caseId;
    private String uploadedBy;
    private String filename;

    // Document metadata fields
    private String mimeType;
    private Long sizeBytes;
    private String storagePath;
    private String checksum;

    // Audit and classification fields
    private LocalDateTime uploadedAt;
    private List<String> tags;
    private Map<String, Object> metadata;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields: id, caseId, and filename must not be null or blank
        return id != null && !id.isBlank() &&
               caseId != null && !caseId.isBlank() &&
               filename != null && !filename.isBlank();
    }
}


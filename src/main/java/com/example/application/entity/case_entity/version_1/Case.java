package com.example.application.entity.case_entity.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Case Entity - Represents a compliance case in the Compliance Management Platform
 * 
 * This entity tracks compliance cases with their status, assignments, evidence,
 * and audit trail for complete compliance tracking and reporting.
 */
@Data
public class Case implements CyodaEntity {
    public static final String ENTITY_NAME = "Case";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String id;

    // Core business fields
    private String title;
    private String description;
    private String createdBy;
    private List<String> assignees;
    private CaseStatus status;
    private List<String> alerts;
    private List<String> evidences;

    // Temporal fields
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    // Audit and metadata
    private List<AuditEvent> auditTrail;
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
        // Validate required fields: id and title must not be null or blank
        return id != null && !id.isBlank() && title != null && !title.isBlank();
    }

    /**
     * Case status enumeration
     */
    public enum CaseStatus {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        ESCALATED
    }

    /**
     * Nested class for audit trail events
     * Tracks all actions performed on the case
     */
    @Data
    public static class AuditEvent {
        private LocalDateTime timestamp;
        private String actor;
        private String action;
        private String details;
    }
}


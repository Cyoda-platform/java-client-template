package com.example.application.entity.audit_log.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuditLog Entity - Tracks all entity changes
 * Implements CyodaEntity for Cyoda workflow integration.
 * Records CREATE, UPDATE, DELETE operations with user and timestamp information.
 */
@Data
public class AuditLog implements CyodaEntity {
    public static final String ENTITY_NAME = "AuditLog";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - UUID
    @NotNull(message = "Audit log ID cannot be null")
    private UUID auditLogId;

    // Reference to the entity being audited
    @NotNull(message = "Entity ID cannot be null")
    private UUID entityId;

    @NotBlank(message = "Entity type is required")
    @Size(max = 100, message = "Entity type must not exceed 100 characters")
    private String entityType;

    // Action performed
    @NotNull(message = "Action is required")
    private AuditAction action;

    // User who performed the action
    @NotBlank(message = "Performed by is required")
    @Size(max = 255, message = "Performed by must not exceed 255 characters")
    private String performedBy;

    // Timestamp of the action
    @NotNull(message = "Performed at timestamp is required")
    private LocalDateTime performedAt;

    // Detailed information about the change
    @Size(max = 4000, message = "Details must not exceed 4000 characters")
    private String details;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return auditLogId != null &&
               entityId != null &&
               entityType != null && !entityType.isBlank() &&
               action != null &&
               performedBy != null && !performedBy.isBlank() &&
               performedAt != null;
    }

    /**
     * Audit Action Enum
     */
    public enum AuditAction {
        CREATE,
        UPDATE,
        DELETE
    }
}


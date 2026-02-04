package com.example.application.dto;

import com.example.application.entity.audit_log.version_1.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuditLog Data Transfer Object
 * Used for API request/response payloads with validation.
 * Provides bidirectional mapping with AuditLog entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDTO {
    private UUID auditLogId;

    @NotNull(message = "Entity ID cannot be null")
    private UUID entityId;

    @NotBlank(message = "Entity type is required")
    @Size(max = 100, message = "Entity type must not exceed 100 characters")
    private String entityType;

    @NotNull(message = "Action is required")
    private String action;

    @NotBlank(message = "Performed by is required")
    @Size(max = 255, message = "Performed by must not exceed 255 characters")
    private String performedBy;

    @NotNull(message = "Performed at timestamp is required")
    private LocalDateTime performedAt;

    @Size(max = 4000, message = "Details must not exceed 4000 characters")
    private String details;

    /**
     * Convert Entity to DTO
     */
    public static AuditLogDTO fromEntity(AuditLog entity) {
        if (entity == null) {
            return null;
        }
        return AuditLogDTO.builder()
                .auditLogId(entity.getAuditLogId())
                .entityId(entity.getEntityId())
                .entityType(entity.getEntityType())
                .action(entity.getAction() != null ? entity.getAction().name() : null)
                .performedBy(entity.getPerformedBy())
                .performedAt(entity.getPerformedAt())
                .details(entity.getDetails())
                .build();
    }

    /**
     * Convert DTO to Entity
     */
    public AuditLog toEntity() {
        AuditLog entity = new AuditLog();
        entity.setAuditLogId(this.auditLogId);
        entity.setEntityId(this.entityId);
        entity.setEntityType(this.entityType);
        entity.setAction(this.action != null ? AuditLog.AuditAction.valueOf(this.action) : null);
        entity.setPerformedBy(this.performedBy);
        entity.setPerformedAt(this.performedAt);
        entity.setDetails(this.details);
        return entity;
    }
}


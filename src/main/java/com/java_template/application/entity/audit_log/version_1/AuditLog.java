package com.java_template.application.entity.audit_log.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * AuditLog Entity - Immutable audit trail for compliance and surveillance
 * Records all significant events: orders, trades, configuration changes, risk breaches
 */
@Data
public class AuditLog implements CyodaEntity {
    public static final String ENTITY_NAME = AuditLog.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique log ID
    private String logId;

    // Entity type: ORDER, TRADE, PORTFOLIO, RISK_RULE, USER, etc.
    private String entityType;

    // Entity ID being audited
    private String entityId;

    // Action: CREATE, UPDATE, DELETE, EXECUTE, REJECT, CANCEL
    private String action;

    // User ID who performed the action
    private String userId;

    // User name
    private String userName;

    // Timestamp of the action
    private LocalDateTime actionTime;

    // Previous state (JSON string)
    private String previousState;

    // New state (JSON string)
    private String newState;

    // Change details (JSON string)
    private String changeDetails;

    // IP address of the user
    private String ipAddress;

    // Result: SUCCESS, FAILURE
    private String result;

    // Error message (if failed)
    private String errorMessage;

    // Additional context/notes
    private String notes;

    // Log creation timestamp
    private LocalDateTime createdAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return logId != null && !logId.trim().isEmpty() &&
               entityType != null && !entityType.trim().isEmpty() &&
               entityId != null && !entityId.trim().isEmpty() &&
               action != null && !action.trim().isEmpty() &&
               userId != null && !userId.trim().isEmpty();
    }
}


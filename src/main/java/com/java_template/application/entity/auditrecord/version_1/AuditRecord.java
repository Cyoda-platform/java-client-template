package com.java_template.application.entity.auditrecord.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * AuditRecord Entity - Immutable audit log for compliance and surveillance
 * Records all significant events: orders, trades, risk checks, compliance actions
 */
@Data
public class AuditRecord implements CyodaEntity {
    public static final String ENTITY_NAME = "AuditRecord";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifiers
    private String auditId;
    private String accountId;
    private String relatedEntityId; // Order, Trade, Account, etc.
    private String relatedEntityType; // ORDER, TRADE, ACCOUNT, LIMIT, etc.

    // Event details
    private String eventType; // ORDER_CREATED, ORDER_FILLED, RISK_CHECK_FAILED, etc.
    private String action; // CREATE, UPDATE, DELETE, APPROVE, REJECT
    private String status; // SUCCESS, FAILURE, PENDING

    // User and system info
    private String userId;
    private String systemComponent; // RiskEngine, ComplianceEngine, etc.
    private String ipAddress;

    // Event data
    private String description;
    private String details; // JSON string of event details
    private String previousState;
    private String newState;

    // Compliance
    private String complianceRule; // Rule that was checked
    private String surveillanceFlag; // SUSPICIOUS, NORMAL, ALERT
    private String regulatoryCategory; // For reporting

    // Timestamps (immutable)
    private LocalDateTime eventTime;
    private LocalDateTime recordedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return auditId != null && !auditId.isBlank() &&
               eventType != null && !eventType.isBlank() &&
               eventTime != null;
    }
}


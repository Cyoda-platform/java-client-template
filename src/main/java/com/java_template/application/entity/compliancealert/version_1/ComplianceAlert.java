package com.java_template.application.entity.compliancealert.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ComplianceAlert Entity - Represents AML/compliance alerts and cases
 * Tracks suspicious activities and compliance investigations
 */
@Data
public class ComplianceAlert implements CyodaEntity {
    public static final String ENTITY_NAME = "ComplianceAlert";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String alertId;

    // Alert type
    private String type; // "SANCTIONS_HIT", "UNUSUAL_ACTIVITY", "LARGE_TRANSACTION", "RAPID_WITHDRAWAL", "PEP_MATCH"

    // Severity level
    private String severity; // "LOW", "MEDIUM", "HIGH", "CRITICAL"

    // Related entity
    private String relatedEntityId; // User, Account, Transaction, etc.
    private String relatedEntityType; // "USER", "ACCOUNT", "TRANSACTION", "TRADE"

    // Alert details
    private String description;
    private String riskScore; // e.g., "75" (0-100)

    // Alert status
    private String status; // "OPEN", "UNDER_REVIEW", "RESOLVED", "ESCALATED", "CLOSED"

    // Investigation
    private String investigationNotes;
    private String resolution;

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime resolvedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return alertId != null && !alertId.isBlank() &&
               type != null && !type.isBlank() &&
               relatedEntityId != null && !relatedEntityId.isBlank();
    }
}


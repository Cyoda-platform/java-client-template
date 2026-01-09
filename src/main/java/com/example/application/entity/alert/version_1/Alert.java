package com.example.application.entity.alert.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Alert Entity for Compliance Management Platform
 * 
 * Represents a compliance alert triggered by various monitoring systems
 * (watchlist hits, velocity checks, unusual activity detection, etc.)
 */
@Data
public class Alert implements CyodaEntity {
    public static final String ENTITY_NAME = "Alert";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String id;

    // Core alert information
    private String transactionId;
    private String customerId;
    private String alertType;
    private Severity severity;
    private Status status;

    // Audit and assignment fields
    private LocalDateTime createdAt;
    private String assignedTo;
    private String relatedCaseId;

    // Additional data
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
        // Validate required business identifier
        return id != null && !id.isBlank();
    }

    /**
     * Alert severity enumeration
     */
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH
    }

    /**
     * Alert status enumeration
     */
    public enum Status {
        OPEN,
        IN_REVIEW,
        ESCALATED,
        CLOSED
    }
}


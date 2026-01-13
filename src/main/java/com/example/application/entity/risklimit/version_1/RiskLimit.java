package com.example.application.entity.risklimit.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * RiskLimit entity representing risk control thresholds for an account
 */
@Data
public class RiskLimit implements CyodaEntity {
    public static final String ENTITY_NAME = "RiskLimit";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String limitId;

    // Core fields
    private String accountId;
    private String limitType; // DAILY_LOSS, POSITION, ORDER_VALUE, EXPOSURE, VAR
    private Double limitValue;
    private Double currentUsage;
    private Double utilizationPercent;
    private String status; // ACTIVE, BREACHED, SUSPENDED

    // Thresholds
    private Double warningThreshold; // Alert when usage exceeds this %
    private Double criticalThreshold; // Kill switch when usage exceeds this %

    // Enforcement
    private Boolean enforcePreTrade;
    private Boolean enforceRealTime;
    private String action; // WARN, BLOCK, KILL_SWITCH

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime breachedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return limitId != null && !limitId.isBlank() &&
               accountId != null && !accountId.isBlank() &&
               limitValue != null && limitValue > 0;
    }
}


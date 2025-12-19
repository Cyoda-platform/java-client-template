package com.java_template.application.entity.limit.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Limit Entity - Represents risk limits and thresholds
 * Used for pre-trade and real-time risk checks
 */
@Data
public class Limit implements CyodaEntity {
    public static final String ENTITY_NAME = "Limit";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifiers
    private String limitId;
    private String accountId;
    private String limitType; // NOTIONAL, DELTA, GROSS_EXPOSURE, MARGIN, LOSS, CONCENTRATION

    // Limit configuration
    private Double limitValue;
    private Double warningThreshold; // Percentage of limit
    private String currency;
    private String scope; // ACCOUNT, PORTFOLIO, INSTRUMENT

    // Limit state
    private Double currentUsage;
    private Double availableCapacity;
    private String status; // ACTIVE, BREACHED, SUSPENDED
    private String breachAction; // REJECT, THROTTLE, NOTIFY, LIQUIDATE

    // Monitoring
    private Integer breachCount;
    private LocalDateTime lastBreachAt;
    private LocalDateTime lastResetAt;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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


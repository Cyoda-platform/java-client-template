package com.java_template.application.entity.risk_rule.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * RiskRule Entity - Represents risk control rules
 * Supports pre-trade checks, intra-day limits, and post-trade risk evaluation
 */
@Data
public class RiskRule implements CyodaEntity {
    public static final String ENTITY_NAME = RiskRule.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique rule ID
    private String ruleId;

    // Rule name
    private String ruleName;

    // Rule description
    private String description;

    // Rule type: PRE_TRADE, INTRA_DAY, POST_TRADE
    private String ruleType;

    // Risk metric: ORDER_SIZE, NOTIONAL_VALUE, DELTA_EXPOSURE, CREDIT_LIMIT, CONCENTRATION
    private String riskMetric;

    // Limit value
    private Double limitValue;

    // Threshold percentage (for alerts)
    private Double thresholdPercent;

    // Action on breach: REJECT, ALERT, THROTTLE
    private String actionOnBreach;

    // Applicable to: PORTFOLIO, INSTRUMENT, USER, ACCOUNT
    private String applicableTo;

    // Target ID (portfolio ID, instrument symbol, user ID, etc.)
    private String targetId;

    // Rule status: ACTIVE, INACTIVE
    private String status;

    // Rule creation timestamp
    private LocalDateTime createdAt;

    // Rule last updated timestamp
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
        return ruleId != null && !ruleId.trim().isEmpty() &&
               ruleName != null && !ruleName.trim().isEmpty() &&
               ruleType != null && !ruleType.trim().isEmpty() &&
               riskMetric != null && !riskMetric.trim().isEmpty() &&
               limitValue != null && limitValue > 0;
    }
}


package com.java_template.application.entity.position.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Position Entity - Represents current holdings in an account
 * Real-time aggregation of trades and fills
 */
@Data
public class Position implements CyodaEntity {
    public static final String ENTITY_NAME = "Position";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifiers
    private String positionId;
    private String accountId;
    private String symbol;
    private String assetType; // EQUITY, OPTION, FUTURE

    // Position details
    private Double quantity;
    private Double averageCost;
    private Double currentPrice;
    private Double marketValue;

    // P&L calculations
    private Double unrealizedPnL;
    private Double realizedPnL;
    private Double totalPnL;
    private Double pnlPercentage;

    // Risk metrics
    private Double notionalValue;
    private Double delta; // For options
    private Double gamma;
    private Double vega;
    private Double theta;

    // Timestamps
    private LocalDateTime openedAt;
    private LocalDateTime lastUpdatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return positionId != null && !positionId.isBlank() &&
               accountId != null && !accountId.isBlank() &&
               symbol != null && !symbol.isBlank();
    }
}


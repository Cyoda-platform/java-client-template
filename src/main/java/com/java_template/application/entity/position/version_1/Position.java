package com.java_template.application.entity.position.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Position Entity - Represents a holding in a specific instrument
 * Tracks quantity, cost basis, current value, P&L, and Greeks for derivatives
 */
@Data
public class Position implements CyodaEntity {
    public static final String ENTITY_NAME = Position.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique position ID
    private String positionId;

    // Portfolio ID that owns this position
    private String portfolioId;

    // Instrument symbol
    private String instrumentSymbol;

    // Current quantity held
    private Long quantity;

    // Average cost per unit
    private Double averageCost;

    // Total cost basis (quantity * averageCost)
    private Double costBasis;

    // Current market price
    private Double currentPrice;

    // Current market value (quantity * currentPrice)
    private Double marketValue;

    // Unrealized P&L
    private Double unrealizedPnl;

    // Unrealized P&L percentage
    private Double unrealizedPnlPercent;

    // Realized P&L (from closed portions)
    private Double realizedPnl;

    // Greeks for derivative positions
    private PositionGreeks greeks;

    // Position creation timestamp
    private LocalDateTime createdAt;

    // Position last updated timestamp
    private LocalDateTime updatedAt;

    // Last mark-to-market timestamp
    private LocalDateTime lastMarkToMarketTime;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return positionId != null && !positionId.trim().isEmpty() &&
               portfolioId != null && !portfolioId.trim().isEmpty() &&
               instrumentSymbol != null && !instrumentSymbol.trim().isEmpty() &&
               quantity != null &&
               averageCost != null && averageCost >= 0;
    }

    /**
     * Greeks for derivative positions
     */
    @Data
    public static class PositionGreeks {
        // Delta: position delta (quantity * instrument delta)
        private Double delta;

        // Gamma: position gamma
        private Double gamma;

        // Vega: position vega
        private Double vega;

        // Theta: position theta (daily time decay)
        private Double theta;

        // Rho: position rho
        private Double rho;
    }
}


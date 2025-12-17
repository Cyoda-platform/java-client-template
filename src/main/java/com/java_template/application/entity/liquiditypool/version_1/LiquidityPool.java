package com.java_template.application.entity.liquiditypool.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * LiquidityPool Entity - Represents a liquidity pool for market-making
 * Manages inventory and spreads for a trading pair
 */
@Data
public class LiquidityPool implements CyodaEntity {
    public static final String ENTITY_NAME = "LiquidityPool";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String poolId;

    // References
    private String marketId;

    // Provider information
    private String provider; // e.g., "INTERNAL", "EXTERNAL_PROVIDER_NAME"

    // Pool balances
    private BigDecimal balanceBase; // Balance of base asset
    private BigDecimal balanceQuote; // Balance of quote asset

    // Liquidity strategy
    private String strategy; // e.g., "MARKET_MAKER", "ARBITRAGE", "PASSIVE"

    // Spread configuration
    private BigDecimal bidSpread; // Bid spread percentage
    private BigDecimal askSpread; // Ask spread percentage

    // Pool status
    private String status; // "ACTIVE", "PAUSED", "REBALANCING", "CLOSED"

    // Performance metrics
    private BigDecimal totalVolume; // Total trading volume
    private BigDecimal totalFees; // Total fees earned

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastRebalanceAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return poolId != null && !poolId.isBlank() &&
               marketId != null && !marketId.isBlank() &&
               balanceBase != null && balanceBase.compareTo(BigDecimal.ZERO) >= 0 &&
               balanceQuote != null && balanceQuote.compareTo(BigDecimal.ZERO) >= 0;
    }
}


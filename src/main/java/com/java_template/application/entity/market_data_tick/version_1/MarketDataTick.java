package com.java_template.application.entity.market_data_tick.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * MarketDataTick Entity - Represents market data snapshots
 * Immutable time-series data for backtesting and replay
 */
@Data
public class MarketDataTick implements CyodaEntity {
    public static final String ENTITY_NAME = MarketDataTick.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique tick ID (timestamp + symbol + exchange)
    private String tickId;

    // Instrument symbol
    private String instrumentSymbol;

    // Exchange source
    private String exchange;

    // Tick timestamp
    private LocalDateTime timestamp;

    // Bid price
    private Double bidPrice;

    // Bid size/volume
    private Long bidSize;

    // Ask price
    private Double askPrice;

    // Ask size/volume
    private Long askSize;

    // Last trade price
    private Double lastPrice;

    // Last trade size
    private Long lastSize;

    // Daily open price
    private Double openPrice;

    // Daily high price
    private Double highPrice;

    // Daily low price
    private Double lowPrice;

    // Daily close price
    private Double closePrice;

    // Daily volume
    private Long volume;

    // Daily VWAP (Volume Weighted Average Price)
    private Double vwap;

    // Implied volatility (for derivatives)
    private Double impliedVolatility;

    // Tick creation timestamp
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
        return tickId != null && !tickId.trim().isEmpty() &&
               instrumentSymbol != null && !instrumentSymbol.trim().isEmpty() &&
               exchange != null && !exchange.trim().isEmpty() &&
               timestamp != null &&
               bidPrice != null && bidPrice > 0 &&
               askPrice != null && askPrice > 0;
    }
}


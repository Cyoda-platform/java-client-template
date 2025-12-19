package com.java_template.application.entity.marketdata.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MarketData Entity - Represents real-time market data (ticks, quotes, order book)
 * Normalized across multiple venues
 */
@Data
public class MarketData implements CyodaEntity {
    public static final String ENTITY_NAME = "MarketData";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifiers
    private String marketDataId;
    private String symbol;
    private String assetType; // EQUITY, OPTION, FUTURE
    private String venue; // Exchange or data source

    // Quote data
    private Double bid;
    private Double ask;
    private Double bidSize;
    private Double askSize;
    private Double last;
    private Double lastSize;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Double volume;

    // Order book (top of book + depth)
    private List<BookLevel> bidLevels;
    private List<BookLevel> askLevels;

    // Greeks (for options)
    private Double impliedVolatility;
    private Double delta;
    private Double gamma;
    private Double vega;
    private Double theta;

    // Timestamps
    private LocalDateTime quoteTime;
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
        return marketDataId != null && !marketDataId.isBlank() &&
               symbol != null && !symbol.isBlank() &&
               bid != null && ask != null && bid > 0 && ask > 0;
    }

    @Data
    public static class BookLevel {
        private Double price;
        private Double size;
        private Integer orders;
    }
}


package com.example.application.entity.marketquote.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * MarketQuote entity representing real-time market data for a security
 */
@Data
public class MarketQuote implements CyodaEntity {
    public static final String ENTITY_NAME = "MarketQuote";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String quoteId;

    // Core fields
    private String symbol;
    private Double bid;
    private Double ask;
    private Integer bidSize;
    private Integer askSize;
    private Double last;
    private Integer lastSize;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Long volume;

    // Market data provider
    private String dataProvider;
    private String exchange;

    // Timestamps
    private LocalDateTime quoteTime;
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
        return quoteId != null && !quoteId.isBlank() &&
               symbol != null && !symbol.isBlank() &&
               bid != null && bid > 0 &&
               ask != null && ask > 0;
    }
}


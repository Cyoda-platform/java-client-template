package com.java_template.application.entity.market.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Market Entity - Represents a trading pair (e.g., BTC/USD)
 * Defines market configuration and trading parameters
 */
@Data
public class Market implements CyodaEntity {
    public static final String ENTITY_NAME = "Market";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String marketId;

    // Asset pair
    private String baseAssetId; // e.g., BTC
    private String quoteAssetId; // e.g., USD

    // Market configuration
    private BigDecimal tickSize; // Minimum price increment
    private BigDecimal minOrderSize; // Minimum order quantity
    private BigDecimal maxOrderSize; // Maximum order quantity

    // Market status
    private String status; // e.g., "ACTIVE", "SUSPENDED", "CLOSED"

    // Trading hours (optional)
    private String tradingHours; // e.g., "24/7" or specific hours

    // Metadata
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
        return marketId != null && !marketId.isBlank() &&
               baseAssetId != null && !baseAssetId.isBlank() &&
               quoteAssetId != null && !quoteAssetId.isBlank() &&
               tickSize != null && tickSize.compareTo(BigDecimal.ZERO) > 0;
    }
}


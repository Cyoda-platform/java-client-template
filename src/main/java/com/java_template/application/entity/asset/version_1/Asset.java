package com.java_template.application.entity.asset.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Asset Entity - Represents a cryptocurrency or fiat asset supported by the exchange
 * Defines asset properties, decimals, and trading status
 */
@Data
public class Asset implements CyodaEntity {
    public static final String ENTITY_NAME = "Asset";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String assetId;

    // Asset information
    private String symbol; // e.g., "BTC", "ETH", "USD"
    private String name; // e.g., "Bitcoin", "Ethereum", "US Dollar"
    private String type; // e.g., "CRYPTO", "FIAT"

    // Decimal places for precision
    private Integer decimals; // e.g., 8 for BTC, 18 for ETH

    // Asset status
    private String status; // e.g., "ACTIVE", "SUSPENDED", "DELISTED"

    // Trading configuration
    private Boolean tradingEnabled;
    private Boolean depositEnabled;
    private Boolean withdrawalEnabled;

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
        return assetId != null && !assetId.isBlank() &&
               symbol != null && !symbol.isBlank() &&
               decimals != null && decimals >= 0;
    }
}


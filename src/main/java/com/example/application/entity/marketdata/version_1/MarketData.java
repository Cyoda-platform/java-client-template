package com.example.application.entity.marketdata.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;

/**
 * MarketData Entity - Represents market data for an instrument
 * Implements CyodaEntity for workflow integration
 */
@Data
public class MarketData implements CyodaEntity {
    public static final String ENTITY_NAME = MarketData.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String id;

    // Core market data fields
    private String instrumentId;
    private BigDecimal price;
    private String timestamp;
    private String source;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return id != null && !id.isBlank() &&
               instrumentId != null && !instrumentId.isBlank() &&
               price != null && price.compareTo(BigDecimal.ZERO) >= 0 &&
               timestamp != null && !timestamp.isBlank() &&
               source != null && !source.isBlank();
    }
}


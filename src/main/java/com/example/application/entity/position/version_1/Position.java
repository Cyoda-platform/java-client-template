package com.example.application.entity.position.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;

/**
 * Position Entity - Represents a portfolio position
 * Implements CyodaEntity for workflow integration
 */
@Data
public class Position implements CyodaEntity {
    public static final String ENTITY_NAME = Position.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String id;

    // Core position fields
    private String portfolioId;
    private String instrumentId;
    private BigDecimal quantity;
    private BigDecimal marketValue;
    private String valuationDate;

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
               portfolioId != null && !portfolioId.isBlank() &&
               instrumentId != null && !instrumentId.isBlank() &&
               quantity != null &&
               marketValue != null &&
               valuationDate != null && !valuationDate.isBlank();
    }
}


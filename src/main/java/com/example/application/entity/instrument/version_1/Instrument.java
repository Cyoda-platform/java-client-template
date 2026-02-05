package com.example.application.entity.instrument.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

/**
 * Instrument Entity - Represents a financial instrument
 * Implements CyodaEntity for workflow integration
 */
@Data
public class Instrument implements CyodaEntity {
    public static final String ENTITY_NAME = Instrument.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String id;

    // Core instrument fields
    private String isin;
    private String ticker;
    private InstrumentType type;
    private String currency;
    private String issuer;
    private String maturityDate;

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
               isin != null && !isin.isBlank() &&
               ticker != null && !ticker.isBlank() &&
               type != null &&
               currency != null && !currency.isBlank() &&
               issuer != null && !issuer.isBlank();
    }

    /**
     * Enum for instrument type
     */
    public enum InstrumentType {
        EQUITY, BOND, FX, DERIVATIVE
    }
}


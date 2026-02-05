package com.example.application.entity.counterparty.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;

/**
 * Counterparty Entity - Represents a trading counterparty
 * Implements CyodaEntity for workflow integration
 */
@Data
public class Counterparty implements CyodaEntity {
    public static final String ENTITY_NAME = Counterparty.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String id;

    // Core counterparty fields
    private String name;
    private String legalEntityId;
    private String country;
    private String rating;
    private BigDecimal defaultProbability;

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
               name != null && !name.isBlank() &&
               legalEntityId != null && !legalEntityId.isBlank() &&
               country != null && !country.isBlank();
    }
}


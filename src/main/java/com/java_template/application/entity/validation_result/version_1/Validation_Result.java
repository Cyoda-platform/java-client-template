package com.java_template.application.entity.validation_result.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import lombok.Getter;
import lombok.AccessLevel;

import java.util.List;

@Data
public class Validation_Result implements CyodaEntity {
    public static final String ENTITY_NAME = "Validation_Result"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    // Error message describing validation problems (if any)
    private String errorMessage;

    // Flag coming from source indicating whether the subject was considered valid
    // Suppress Lombok-generated getter to avoid conflict with CyodaEntity.isValid()
    @Getter(AccessLevel.NONE)
    private Boolean isValid;

    // List of missing field names when validation failed
    private List<String> missingFields;

    public Validation_Result() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // If there is a non-blank error message, the entity is invalid
        if (this.errorMessage != null && !this.errorMessage.isBlank()) {
            return false;
        }
        // If there are any missing fields, the entity is invalid
        if (this.missingFields != null && !this.missingFields.isEmpty()) {
            return false;
        }
        // Otherwise consider it valid
        return true;
    }
}
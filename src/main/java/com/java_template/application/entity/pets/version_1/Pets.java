package com.java_template.application.entity.pets.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Pets implements CyodaEntity {
    public static final String ENTITY_NAME = "Pets"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id (serialized UUID as String)
    private String id;
    private String breed;
    private String species;
    private String status;

    public Pets() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields. Use isBlank checks to catch empty values.
        if (this.species == null || this.species.isBlank()) {
            return false;
        }
        if (this.status == null || this.status.isBlank()) {
            return false;
        }
        // breed is optional based on prototype example, so no strict check here.
        return true;
    }
}
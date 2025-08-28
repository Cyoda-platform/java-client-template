package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet"; 
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on prototype
    // Technical identifier for the pet (serialized UUID or external id)
    private String petId;
    // Human-readable name
    private String name;
    // Species (e.g., "cat", "dog")
    private String species;
    // Breed (optional)
    private String breed;
    // Color (optional)
    private String color;
    // Age in months
    private Integer ageMonths;
    // Source system identifier (e.g., "PETSTORE_API")
    private String source;
    // Status (e.g., "AVAILABLE")
    private String status;
    // ISO-8601 ingestion timestamp as String
    private String ingestedAt;

    public Pet() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank()
        if (petId == null || petId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (source == null || source.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (ingestedAt == null || ingestedAt.isBlank()) return false;

        // Validate numeric fields
        if (ageMonths == null || ageMonths < 0) return false;

        // Optional fields (breed, color) need no validation
        return true;
    }
}
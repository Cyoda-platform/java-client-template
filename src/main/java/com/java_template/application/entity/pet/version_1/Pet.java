package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: Pet entity represents a pet record collected from the pets API.
 * Contains pet information including name, species, breed, age, and owner details.
 */
@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = Pet.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String petId;
    
    // Core pet information
    private String name;
    private String species;
    private String breed;
    private Integer age;
    private String color;
    
    // Owner information
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    
    // Additional pet details
    private String microchipId;
    private String vaccinated;
    private String neutered;
    private String notes;
    
    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
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
        // Validate required fields
        return petId != null && !petId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               species != null && !species.trim().isEmpty();
    }
}


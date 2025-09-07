package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Pet Entity - Represents a pet in the Purrfect Pets system
 * 
 * This entity manages pet information including basic details,
 * health information, and owner relationships.
 */
@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = Pet.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String petId;
    
    // Required core business fields
    private String name;
    private String species;
    private String breed;
    private Integer age;
    private Double weight;
    private String color;
    private String ownerId;
    
    // Optional fields for additional business data
    private String healthNotes;
    private String photoUrl;
    private LocalDateTime registrationDate;
    private LocalDateTime lastCheckupDate;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return petId != null && !petId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               species != null && !species.trim().isEmpty() &&
               ownerId != null && !ownerId.trim().isEmpty() &&
               age != null && age >= 0 &&
               weight != null && weight > 0;
    }
}

package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Pet Entity - Represents individual pets available for adoption in the Purrfect Pets system
 * 
 * Purpose: Manages pet information throughout the adoption lifecycle
 * States: initial_state -> available -> reserved -> adopted
 */
@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = Pet.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String petId;
    
    // Required core business fields
    private String name;
    private String species;
    private Integer age;
    private String healthStatus;
    private LocalDateTime arrivalDate;

    // Optional fields for additional business data
    private String breed;
    private String color;
    private String size;
    private String description;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields according to business rules
        return petId != null && !petId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               species != null && !species.trim().isEmpty() &&
               age != null && age > 0 &&
               healthStatus != null && !healthStatus.trim().isEmpty() &&
               arrivalDate != null;
    }
}

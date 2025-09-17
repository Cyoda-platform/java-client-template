package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Pet Entity - Represents animals available for adoption in the Purrfect Pets system
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
    
    // Optional fields for additional business data
    private String breed;
    private Integer age;
    private String gender;
    private String size;
    private String color;
    private String description;
    private String healthStatus;
    private Boolean vaccinated;
    private Boolean spayedNeutered;
    private String specialNeeds;
    private LocalDateTime arrivalDate;
    private Double adoptionFee;

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
               (age == null || age >= 0); // Age should be non-negative if provided
    }
}

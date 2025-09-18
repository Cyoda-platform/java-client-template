package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;

/**
 * Pet Entity - Represents animals available for adoption in the Purrfect Pets system
 * 
 * This entity represents pets with their basic information, medical history,
 * and adoption details. The state is managed via entity metadata workflow.
 */
@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = Pet.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business fields
    private String name;
    private String species;
    private String breed;
    private Integer age;
    private String description;
    private String medicalHistory;
    private Double adoptionFee;
    private LocalDate arrivalDate;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields and business rules
        return name != null && !name.trim().isEmpty() &&
               species != null && !species.trim().isEmpty() &&
               breed != null && !breed.trim().isEmpty() &&
               age != null && age > 0 &&
               adoptionFee != null && adoptionFee >= 0.0 &&
               medicalHistory != null && !medicalHistory.trim().isEmpty();
    }
}

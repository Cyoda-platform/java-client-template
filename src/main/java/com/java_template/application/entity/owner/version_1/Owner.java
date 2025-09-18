package com.java_template.application.entity.owner.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;

/**
 * Owner Entity - Represents people who can adopt pets from the Purrfect Pets system
 * 
 * This entity represents potential pet adopters with their personal information,
 * contact details, and housing information. The state is managed via entity metadata workflow.
 */
@Data
public class Owner implements CyodaEntity {
    public static final String ENTITY_NAME = Owner.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required personal information
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    
    // Additional information for pet matching
    private String experienceWithPets;
    private String housingType;
    private LocalDate registrationDate;

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
        return firstName != null && !firstName.trim().isEmpty() &&
               lastName != null && !lastName.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() &&
               phone != null && !phone.trim().isEmpty() &&
               address != null && !address.trim().isEmpty();
    }
}

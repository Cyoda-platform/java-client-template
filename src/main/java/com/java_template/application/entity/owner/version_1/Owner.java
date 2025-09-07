package com.java_template.application.entity.owner.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Owner Entity - Represents a pet owner in the Purrfect Pets system
 * 
 * This entity manages owner information including contact details,
 * address information, and pet ownership tracking.
 */
@Data
public class Owner implements CyodaEntity {
    public static final String ENTITY_NAME = Owner.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String ownerId;
    
    // Required core business fields
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String address;
    private String city;
    private String zipCode;
    
    // Optional fields for additional business data
    private String emergencyContact;
    private String preferredVet;
    private LocalDateTime registrationDate;
    private Integer totalPets;

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
        return ownerId != null && !ownerId.trim().isEmpty() &&
               firstName != null && !firstName.trim().isEmpty() &&
               lastName != null && !lastName.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() &&
               phoneNumber != null && !phoneNumber.trim().isEmpty() &&
               address != null && !address.trim().isEmpty() &&
               city != null && !city.trim().isEmpty() &&
               zipCode != null && !zipCode.trim().isEmpty();
    }
}

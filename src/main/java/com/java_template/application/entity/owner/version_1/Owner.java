package com.java_template.application.entity.owner.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Owner Entity - Represents individuals interested in adopting pets from the Purrfect Pets system
 */
@Data
public class Owner implements CyodaEntity {
    public static final String ENTITY_NAME = Owner.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String ownerId;
    
    // Required core business fields
    private String firstName;
    private String lastName;
    private String email;
    
    // Optional fields for additional business data
    private String phone;
    private Address address;
    private LocalDate dateOfBirth;
    private String occupation;
    private String housingType;
    private Boolean hasYard;
    private Boolean hasOtherPets;
    private PetPreferences petPreferences;
    private LocalDateTime registrationDate;
    private String verificationStatus;

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
        return ownerId != null && !ownerId.trim().isEmpty() &&
               firstName != null && !firstName.trim().isEmpty() &&
               lastName != null && !lastName.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() && isValidEmail(email);
    }

    /**
     * Simple email validation
     */
    private boolean isValidEmail(String email) {
        return email.contains("@") && email.contains(".");
    }

    /**
     * Nested class for address information
     */
    @Data
    public static class Address {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String zipCode;
        private String country;
    }

    /**
     * Nested class for pet preferences
     */
    @Data
    public static class PetPreferences {
        private String preferredSpecies;
        private String preferredSize;
        private Integer preferredAge;
        private Double maxAdoptionFee;
    }
}

package com.java_template.application.entity.owner.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

/**
 * Owner Entity - Represents potential or current pet owners in the Purrfect Pets system
 * 
 * Purpose: Manages owner information throughout the registration and approval process
 * States: initial_state -> registered -> verified -> approved
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
    private String phone;
    private OwnerAddress address;

    // Optional fields for additional business data
    private String experience;
    private OwnerPreferences preferences;

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
               email != null && !email.trim().isEmpty() &&
               phone != null && !phone.trim().isEmpty() &&
               address != null && address.isValid();
    }

    /**
     * Nested class for owner address information
     * Contains all address-related fields
     */
    @Data
    public static class OwnerAddress {
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String country;

        public boolean isValid() {
            return street != null && !street.trim().isEmpty() &&
                   city != null && !city.trim().isEmpty() &&
                   state != null && !state.trim().isEmpty() &&
                   zipCode != null && !zipCode.trim().isEmpty() &&
                   country != null && !country.trim().isEmpty();
        }
    }

    /**
     * Nested class for owner pet preferences
     * Used for matching pets with potential owners
     */
    @Data
    public static class OwnerPreferences {
        private String preferredSpecies;
        private String preferredSize;
        private String preferredAge;
    }
}

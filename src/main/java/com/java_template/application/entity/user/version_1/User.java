package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Entity - Represents a user/customer in the pet store system
 * 
 * This entity manages user accounts with their personal information and preferences.
 * The userStatus field from the Petstore API is replaced by the entity.meta.state system.
 * 
 * States: registered, active, suspended, inactive
 */
@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = User.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String userId;
    
    // Required core business fields
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String password;

    // Optional fields for additional business data
    private String phone;
    private List<Address> addresses;
    private UserPreferences preferences;
    private LocalDateTime registrationDate;
    private LocalDateTime lastLoginDate;
    private Boolean isActive;

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
        return userId != null && !userId.trim().isEmpty() &&
               username != null && !username.trim().isEmpty() &&
               firstName != null && !firstName.trim().isEmpty() &&
               lastName != null && !lastName.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() && isValidEmail(email) &&
               password != null && !password.trim().isEmpty();
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
        private String addressId;
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postcode;
        private String country;
        private Boolean isDefault;
    }

    /**
     * Nested class for user preferences
     */
    @Data
    public static class UserPreferences {
        private List<String> preferredCategories; // e.g., ["Dogs", "Cats"]
        private Boolean emailNotifications;
        private Boolean smsNotifications;
        private String preferredContactMethod; // "email" or "sms"
    }
}

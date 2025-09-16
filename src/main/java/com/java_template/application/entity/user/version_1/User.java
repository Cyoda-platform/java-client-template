package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * User Entity - Represents customers and users of the system
 * 
 * This entity manages user account information including personal
 * details, addresses, and preferences. State is managed automatically
 * by the workflow system via entity.meta.state.
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

    // Optional fields for additional business data
    private String phone;
    private List<Address> addresses;
    private LocalDate dateOfBirth;
    private UserPreferences preferences;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields according to functional requirements
        return userId != null && !userId.trim().isEmpty() &&
               username != null && !username.trim().isEmpty() &&
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
     * Used for user addresses with default flag
     */
    @Data
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String country;
        private Boolean isDefault;
    }

    /**
     * Nested class for user preferences
     * Contains user preference settings
     */
    @Data
    public static class UserPreferences {
        private List<String> favoriteCategories;
        private Boolean newsletter;
        private Boolean notifications;
    }
}

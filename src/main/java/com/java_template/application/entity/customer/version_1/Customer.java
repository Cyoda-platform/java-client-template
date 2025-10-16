package com.java_template.application.entity.customer.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Customer entity represents users who can purchase pets from the store
 * with personal information, contact details, and account status managed through workflow states.
 */
@Data
public class Customer implements CyodaEntity {
    public static final String ENTITY_NAME = Customer.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String customerId;
    
    // Required core business fields
    private String username;
    private String email;
    private String firstName;
    private String lastName;

    // Optional fields for additional business data
    private String password; // Encrypted password
    private String phone;
    private CustomerAddress address;
    private LocalDate dateOfBirth;
    private CustomerPreferences preferences;
    private Integer loyaltyPoints;
    private Integer totalOrders;
    private Double totalSpent;
    private LocalDateTime lastLoginAt;
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
    public boolean isValid(org.cyoda.cloud.api.event.common.EntityMetadata metadata) {
        // Validate required fields
        return customerId != null && !customerId.trim().isEmpty() &&
               username != null && !username.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() && isValidEmail(email) &&
               firstName != null && !firstName.trim().isEmpty() &&
               lastName != null && !lastName.trim().isEmpty() &&
               (loyaltyPoints == null || loyaltyPoints >= 0) &&
               (totalOrders == null || totalOrders >= 0) &&
               (totalSpent == null || totalSpent >= 0);
    }

    /**
     * Simple email validation
     */
    private boolean isValidEmail(String email) {
        return email.contains("@") && email.contains(".");
    }

    /**
     * Nested class for customer address information
     */
    @Data
    public static class CustomerAddress {
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String country;
    }

    /**
     * Nested class for customer preferences
     */
    @Data
    public static class CustomerPreferences {
        private List<String> preferredPetTypes;
        private List<String> communicationPreferences;
        private Boolean newsletter;
    }
}

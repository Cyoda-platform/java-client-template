package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * User Entity for Research & Clinical Trial Management platform
 * Represents users with role-based access control
 */
@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = User.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field - email serves as unique identifier
    private String email;
    
    // Required core business fields
    private String firstName;
    private String lastName;
    private String role; // EXTERNAL_SUBMITTER, REVIEWER, ADMIN
    private String organization;
    private Boolean isActive;
    private LocalDateTime registrationDate;

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
        return email != null && !email.trim().isEmpty() &&
               firstName != null && !firstName.trim().isEmpty() &&
               lastName != null && !lastName.trim().isEmpty() &&
               role != null && !role.trim().isEmpty() &&
               organization != null && !organization.trim().isEmpty() &&
               isActive != null &&
               registrationDate != null &&
               isValidRole(role) &&
               isValidEmail(email);
    }

    /**
     * Validates if the role is one of the allowed values
     */
    private boolean isValidRole(String role) {
        return "EXTERNAL_SUBMITTER".equals(role) || 
               "REVIEWER".equals(role) || 
               "ADMIN".equals(role);
    }

    /**
     * Basic email validation
     */
    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }
}

package com.java_template.application.entity.customer.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: Customer entity for managing customer information in the system.
 * Implements CyodaEntity for workflow-driven persistence within the Cyoda framework.
 */
@Data
public class Customer implements CyodaEntity {
    public static final String ENTITY_NAME = Customer.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique customer ID
    private String customerId;

    // Required customer information
    private String firstName;
    private String lastName;
    private String email;

    // Optional customer information
    private String phone;

    // Audit timestamps
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
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields for customer
        return customerId != null && !customerId.isBlank()
                && firstName != null && !firstName.isBlank()
                && lastName != null && !lastName.isBlank()
                && email != null && !email.isBlank()
                && isValidEmail(email);
    }

    /**
     * Validates email format using a basic pattern
     */
    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}


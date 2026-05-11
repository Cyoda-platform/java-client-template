package com.example.application.entity.customer.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.OffsetDateTime;

/**
 * Customer Entity - Standard Profile Implementation
 * Implements CRUD operations with Authenticated Business features
 * Includes audit fields, soft delete, and address nesting
 */
@Data
public class Customer implements CyodaEntity {
    public static final String ENTITY_NAME = "Customer";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique email
    private String id; // UUID string

    // Core customer fields
    private String firstName;
    private String lastName;
    private String email;
    private String phone;

    // Address information
    private Address address;

    // Audit fields
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    // Soft delete flag
    private Boolean deleted = false;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields: email and name are mandatory
        return email != null && !email.isBlank() &&
               firstName != null && !firstName.isBlank() &&
               lastName != null && !lastName.isBlank();
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
        private String postalCode;
        private String country;
    }
}


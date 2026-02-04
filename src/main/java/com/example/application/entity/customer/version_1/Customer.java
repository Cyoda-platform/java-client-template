package com.example.application.entity.customer.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Entity - Retail Customer Management
 * Implements CyodaEntity for Cyoda workflow integration.
 * Includes nested Address and KYC components with JSR-380 validation.
 */
@Data
public class Customer implements CyodaEntity {
    public static final String ENTITY_NAME = "Customer";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - UUID
    @NotNull(message = "Customer ID cannot be null")
    private UUID customerId;

    // Core customer information
    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone must be a valid international format")
    private String phone;

    @NotNull(message = "Date of birth is required")
    @PastOrPresent(message = "Date of birth cannot be in the future")
    private LocalDate dateOfBirth;

    // Status enum
    @NotNull(message = "Status is required")
    private CustomerStatus status;

    // Nested components
    @Valid
    @NotNull(message = "Address is required")
    private Address address;

    @Valid
    @NotNull(message = "KYC information is required")
    private KYC kyc;

    // Audit fields
    @NotNull(message = "Created at timestamp is required")
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // Soft delete flag
    private Boolean softDeleted = false;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return customerId != null && 
               firstName != null && !firstName.isBlank() &&
               lastName != null && !lastName.isBlank() &&
               email != null && !email.isBlank() &&
               phone != null && !phone.isBlank() &&
               dateOfBirth != null &&
               status != null &&
               address != null &&
               kyc != null;
    }

    /**
     * Customer Status Enum
     */
    public enum CustomerStatus {
        NEW,
        VERIFIED,
        SUSPENDED
    }

    /**
     * Nested Address Component
     */
    @Data
    public static class Address {
        @NotBlank(message = "Street is required")
        @Size(max = 255, message = "Street must not exceed 255 characters")
        private String street;

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        private String city;

        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must not exceed 100 characters")
        private String state;

        @NotBlank(message = "Postal code is required")
        @Size(max = 20, message = "Postal code must not exceed 20 characters")
        private String postalCode;

        @NotBlank(message = "Country is required")
        @Size(max = 100, message = "Country must not exceed 100 characters")
        private String country;
    }

    /**
     * Nested KYC Component
     */
    @Data
    public static class KYC {
        @NotNull(message = "KYC status is required")
        private KYCStatus kycStatus;

        @Size(max = 100, message = "Document type must not exceed 100 characters")
        private String kycDocumentType;

        @Size(max = 255, message = "Document ID must not exceed 255 characters")
        private String kycDocumentId;
    }

    /**
     * KYC Status Enum
     */
    public enum KYCStatus {
        PENDING,
        VERIFIED,
        REJECTED
    }
}


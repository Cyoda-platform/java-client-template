package com.example.application.entity.customer.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Customer Entity for Compliance Management Platform
 * 
 * Represents a customer (individual or business) with KYC information,
 * addresses, and compliance status tracking.
 */
@Data
public class Customer implements CyodaEntity {
    public static final String ENTITY_NAME = "Customer";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String id;

    // Core customer information
    private String legalName;
    private String primaryEmail;
    private String primaryPhone;
    private LocalDate dob;
    private CustomerType customerType;
    private Status status;
    private KycLevel kycLevel;

    // Complex fields
    private List<Address> addresses;
    private Map<String, Object> metadata;

    // Audit fields
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
        // Validate required business identifier
        return id != null && !id.isBlank();
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
        private String postcode;
        private String country;
        private AddressType addressType;
    }

    /**
     * Customer type enumeration
     */
    public enum CustomerType {
        INDIVIDUAL,
        BUSINESS
    }

    /**
     * Customer status enumeration
     */
    public enum Status {
        PENDING,
        VERIFIED,
        SUSPENDED
    }

    /**
     * KYC level enumeration
     */
    public enum KycLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    /**
     * Address type enumeration
     */
    public enum AddressType {
        HOME,
        BUSINESS,
        OTHER
    }
}


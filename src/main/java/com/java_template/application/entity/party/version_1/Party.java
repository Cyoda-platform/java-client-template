package com.java_template.application.entity.party.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: This file contains the Party entity representing borrowers or corporate customers
 * used as reference data for loans and payments in the loan management system.
 */
@Data
@NoArgsConstructor
public class Party implements CyodaEntity {
    public static final String ENTITY_NAME = Party.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    @NonNull
    private String partyId;
    
    // Required core business fields
    private String name;
    private String type; // INDIVIDUAL, CORPORATE
    
    // Contact information
    private PartyContact contact;
    
    // Identity information
    private PartyIdentity identity;
    
    // Status and metadata
    private String status; // ACTIVE, SUSPENDED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return partyId != null && !partyId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               type != null && !type.trim().isEmpty();
    }

    /**
     * Nested class for contact information
     */
    @Data
    public static class PartyContact {
        private String email;
        private String phone;
        private String mobile;
        private PartyAddress address;
    }

    /**
     * Nested class for address information
     */
    @Data
    public static class PartyAddress {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postcode;
        private String country;
    }

    /**
     * Nested class for identity information
     */
    @Data
    public static class PartyIdentity {
        private String taxId;
        private String registrationNumber;
        private String identityType;
        private String identityNumber;
    }
}

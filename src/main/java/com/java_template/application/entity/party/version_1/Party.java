package com.java_template.application.entity.party.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

/**
 * ABOUTME: This entity represents a legal entity (borrower, lender, agent) that can
 * participate in loan agreements within the Commercial Loan Management System.
 */
@Data
public class Party implements CyodaEntity {
    public static final String ENTITY_NAME = Party.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String partyId;
    
    // Required core business fields
    private String legalName;
    private String jurisdiction;
    
    // Optional fields for additional business data
    private String lei; // Legal Entity Identifier
    private String role; // e.g., "Borrower", "Lender", "Agent", "Security Trustee"
    private PartyContact contact;
    private PartyAddress address;

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
        return partyId != null && !partyId.trim().isEmpty() &&
               legalName != null && !legalName.trim().isEmpty() &&
               jurisdiction != null && !jurisdiction.trim().isEmpty();
    }

    /**
     * Nested class for contact information
     */
    @Data
    public static class PartyContact {
        private String contactName;
        private String email;
        private String phone;
        private String fax;
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
}

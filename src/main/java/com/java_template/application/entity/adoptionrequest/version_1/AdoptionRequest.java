package com.java_template.application.entity.adoptionrequest.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequest"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String requestId; // technical id (serialized UUID)
    private String petId; // foreign key reference to Pet (serialized UUID)
    private String applicantName;
    private ContactInfo contactInfo;
    private String requestedAt; // ISO-8601 string
    private String status; // e.g., "under_review", "approved", "rejected"
    private String decisionNotes;
    private Boolean verificationCompleted;

    public AdoptionRequest() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields
        if (requestId == null || requestId.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (applicantName == null || applicantName.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Validate contact info
        if (contactInfo == null) return false;
        if (contactInfo.getEmail() == null || contactInfo.getEmail().isBlank()) return false;
        // phone may be optional but if provided, ensure not blank
        if (contactInfo.getPhone() != null && contactInfo.getPhone().isBlank()) return false;

        // Validate address within contact info
        if (contactInfo.getAddress() == null) return false;
        Address addr = contactInfo.getAddress();
        if (addr.getStreet() == null || addr.getStreet().isBlank()) return false;
        if (addr.getCity() == null || addr.getCity().isBlank()) return false;
        if (addr.getState() == null || addr.getState().isBlank()) return false;
        if (addr.getZip() == null || addr.getZip().isBlank()) return false;

        // decisionNotes may be blank; verificationCompleted may be null (treat as false allowed)
        return true;
    }

    @Data
    public static class ContactInfo {
        private Address address;
        private String email;
        private String phone;
    }

    @Data
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String zip;
    }
}
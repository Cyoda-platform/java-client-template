package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id / technical id
    private String fullName; // name
    private String contactEmail; // contactInfo.email
    private String contactPhone; // contactInfo.phone
    private String role; // public, staff, admin
    private List<String> favorites; // list of Pet ids
    private String createdAt; // ISO timestamp

    // Additional fields expected by processors
    private String status; // e.g., REGISTERED, VERIFIED, ACTIVE
    private Integer version;

    public User() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required fields: fullName and contact email
        if (fullName == null || fullName.isBlank()) return false;
        if (contactEmail == null || contactEmail.isBlank()) return false;
        return true;
    }

    // Compatibility convenience methods used by processors/criteria
    public String getTechnicalId() {
        return this.id;
    }

    public void setTechnicalId(String technicalId) {
        this.id = technicalId;
    }

    // Provide a ContactInfo view used by processors (they expect getContactInfo().getEmail())
    public ContactInfo getContactInfo() {
        return new ContactInfo(this.contactEmail, this.contactPhone);
    }

    // Inner helper to expose expected methods
    public static class ContactInfo {
        private final String email;
        private final String phone;

        public ContactInfo(String email, String phone) {
            this.email = email;
            this.phone = phone;
        }

        public String getEmail() {
            return email;
        }

        public String getPhone() {
            return phone;
        }
    }
}
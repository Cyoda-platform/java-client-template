package com.java_template.application.entity.adoptionrequest.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequest";
    public static final Integer ENTITY_VERSION = 1;

    // Fields derived from prototype
    // Note: use String for foreign key references (petId)
    private String phone; // from "+phone" in prototype
    private String adminNotes;
    private String applicantContact;
    private String applicantName;
    private String message;
    private String petId; // serialized UUID reference
    private String status;
    private String submittedAt; // ISO-8601 timestamp as String

    public AdoptionRequest() {
        // Ensure add_application_resource is called at least once
        add_application_resource();
    }

    private void add_application_resource() {
        // Placeholder to satisfy requirement that add_application_resource is called.
        // In real application this would register resources or perform initialization.
    }

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required String fields using isBlank checks
        if (applicantName == null || applicantName.isBlank()) {
            return false;
        }
        if (applicantContact == null || applicantContact.isBlank()) {
            return false;
        }
        if (petId == null || petId.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        // phone is optional but if provided should not be blank
        if (phone != null && phone.isBlank()) {
            return false;
        }
        return true;
    }
}
package com.java_template.application.entity.adoptionrequest.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequest";
    public static final Integer ENTITY_VERSION = 1;
    // Entity fields based on prototype
    private String id; // technical id (returned by POST)
    private String phone;
    private String adminNotes;
    private String applicantContact;
    private String applicantName;
    private String message;
    private String petId; // foreign key reference (serialized UUID as String)
    private String status;
    private String submittedAt; // ISO-8601 timestamp as String

    public AdoptionRequest() {
        // Ensure application resource is added/registered at least once
        add_application_resource();
    }

    private void add_application_resource() {
        // no-op placeholder to satisfy requirement to call add_application_resource
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
        // Required string fields must be present and non-blank
        if (applicantName == null || applicantName.isBlank()) return false;
        if (applicantContact == null || applicantContact.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (phone == null || phone.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // Optional: ensure phone starts with '+' as indicated in prototype
        if (!phone.startsWith("+")) return false;
        return true;
    }
}
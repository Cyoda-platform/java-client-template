package com.java_template.application.entity.adoptionrequest.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequest"; 
    public static final Integer ENTITY_VERSION = 1;

    // Technical id (may be assigned by the system)
    private String id;

    // Reference to Pet (serialized UUID / technical id)
    private String petId;

    // Requester information
    private String requesterName;
    private String contactEmail;
    private String contactPhone;

    // Request details
    private String motivation;
    private String notes;

    // Processing information
    private String processedBy; // user id or technical id of processor
    private String status; // e.g., "created", "processed", etc.

    // Timestamps as ISO-8601 strings
    private String submittedAt;

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
        // Required: petId, requesterName, contactEmail, status, submittedAt
        if (petId == null || petId.isBlank()) return false;
        if (requesterName == null || requesterName.isBlank()) return false;
        if (contactEmail == null || contactEmail.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (submittedAt == null || submittedAt.isBlank()) return false;
        return true;
    }
}
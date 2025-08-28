package com.java_template.application.entity.adoptionrequest.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequest"; 
    public static final Integer ENTITY_VERSION = 1;
    // Technical id returned by POST endpoints (serialized UUID)
    private String id;
    // Reference to Pet (serialized UUID or external id)
    private String petId;
    // Requester information
    private String requesterName;
    private String requesterContact;
    // Timestamps as ISO-8601 strings
    private String requestedAt;
    private String decisionAt; // may be null if not decided yet
    // Free-form message from requester
    private String message;
    // Status as String (use String for enum-like values)
    private String status;

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
        // Required string fields must be non-null and not blank
        if (petId == null || petId.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;
        if (requesterContact == null || requesterContact.isBlank()) return false;
        if (requesterName == null || requesterName.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // message and decisionAt may be null/blank depending on workflow state
        return true;
    }
}
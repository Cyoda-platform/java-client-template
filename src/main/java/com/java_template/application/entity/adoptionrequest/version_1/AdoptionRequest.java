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
    // Technical id for the adoption request (serialized UUID or external id)
    private String requestId;
    // Reference to the pet (serialized UUID or external id)
    private String petId;
    // Reference to the requester/owner (serialized UUID or external id)
    private String requesterId;
    // Reference to the reviewer (may be null until reviewed)
    private String reviewerId;
    // Status of the request (use String for enum-like values: submitted, approved, rejected, etc.)
    private String status;
    // Optional human-readable notes from the requester or reviewer
    private String notes;
    // ISO-8601 timestamp strings for submission and decision times
    private String submittedAt;
    private String decisionAt;

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
        // Validate required String fields using isBlank()
        if (requestId == null || requestId.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (requesterId == null || requesterId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (submittedAt == null || submittedAt.isBlank()) return false;
        // Optional fields: reviewerId, decisionAt, notes may be null or blank
        return true;
    }
}
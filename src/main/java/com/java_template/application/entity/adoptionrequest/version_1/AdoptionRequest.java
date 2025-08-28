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
    // Technical id (e.g., "REQ-55")
    private String id;
    // Optional message from requester
    private String message;
    // Foreign key reference to Pet (serialized UUID / id string)
    private String petId;
    // Timestamp when processed (ISO string), optional until processed
    private String processedAt;
    // Foreign key reference to processor (serialized UUID / id string), optional
    private String processedBy;
    // Foreign key reference to requester (serialized UUID / id string)
    private String requesterId;
    // Request status (use String for enum-like values e.g., "submitted", "approved", "rejected")
    private String status;
    // Timestamp when submitted (ISO string)
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
        // Validate required string fields using isBlank() to catch empty/whitespace values
        if (id == null || id.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (requesterId == null || requesterId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (submittedAt == null || submittedAt.isBlank()) return false;
        // message, processedAt, processedBy are optional
        return true;
    }
}
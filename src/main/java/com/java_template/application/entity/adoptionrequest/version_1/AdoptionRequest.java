package com.java_template.application.entity.adoptionrequest.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequest"; 
    public static final Integer ENTITY_VERSION = 1;

    // Technical identifier (serialized UUID / string)
    private String id;

    // Reference to the user requesting adoption (serialized UUID / string)
    private String userId;

    // Reference to the pet to be adopted (serialized UUID / string)
    private String petId;

    // ISO-8601 date-time string when the adoption was requested
    private String requestedDate;

    // Status as string (e.g., pending, approved, rejected)
    private String status;

    // Optional notes provided by the requester
    private String notes;

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
        // Required fields must be present and non-blank
        if (id == null || id.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (requestedDate == null || requestedDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
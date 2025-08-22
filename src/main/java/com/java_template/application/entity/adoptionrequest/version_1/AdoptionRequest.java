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
    // Technical id
    private String id;
    // Foreign key references (serialized UUIDs)
    private String ownerId;
    private String petId;
    // Request and decision metadata
    private String requestDate; // ISO datetime string
    private String preferredPickupDate; // ISO date string (yyyy-MM-dd)
    private String decisionBy; // user/staff id who made the decision
    private String decisionDate; // ISO datetime string
    private String status; // use String for enum-like values
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
        // Required fields: id, ownerId, petId, requestDate, status
        if (id == null || id.isBlank()) return false;
        if (ownerId == null || ownerId.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (requestDate == null || requestDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
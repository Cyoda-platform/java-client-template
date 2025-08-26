package com.java_template.application.entity.adoptionrequests.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionRequests implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequests"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id (returned by POST endpoints)
    private String id;
    // Request status (e.g., "pending", "approved", "rejected")
    private String status;
    // Reference to the user who made the request (serialized UUID)
    private String userId;

    public AdoptionRequests() {} 

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
        if (status == null || status.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        return true;
    }
}
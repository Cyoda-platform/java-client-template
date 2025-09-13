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
    private String id;
    private String message;
    // References to other entities stored as serialized UUIDs
    private String petId;
    private String processedBy;
    private String requestedAt;
    private String status;
    private String userId;

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
        // Helper rule: use .isBlank() for String validation (allowing nulls to be treated as invalid)
        if (id == null || id.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // message and processedBy can be optional (processedBy may be null until processed)
        return true;
    }
}
package com.java_template.application.entity.adoptionorder.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionOrder implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionOrder"; 
    public static final Integer ENTITY_VERSION = 1;

    // Technical id (serialized UUID or string id)
    private String id;

    // References to other entities (serialized UUIDs as Strings)
    private String petId;
    private String userId;

    // Dates as ISO-8601 strings
    private String requestedDate;
    private String approvedDate; // nullable
    private String completedDate; // nullable

    // Order details
    private String status;
    private String pickupMethod;
    private String notes;

    public AdoptionOrder() {} 

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
        if (id == null || id.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (requestedDate == null || requestedDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (pickupMethod == null || pickupMethod.isBlank()) return false;

        // notes, approvedDate, completedDate may be null or blank - no further validation here
        return true;
    }
}
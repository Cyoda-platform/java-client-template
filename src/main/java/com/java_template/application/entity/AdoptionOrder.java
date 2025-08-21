package com.java_template.application.entity.adoptionorder.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionOrder implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionOrder";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private Double fees;
    private String notes;
    private String ownerId; // foreign key reference (serialized UUID string)
    private String petId;   // foreign key reference (serialized UUID string)
    private String processedAt; // ISO-8601 timestamp string, nullable
    private String requestedAt; // ISO-8601 timestamp string
    private String status; // use String for enum values

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
        // Validate required string fields (use isBlank checks)
        if (id == null || id.isBlank()) return false;
        if (ownerId == null || ownerId.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // fees can be null or non-negative if present
        if (fees != null && fees < 0) return false;
        return true;
    }
}
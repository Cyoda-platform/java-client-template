package com.java_template.application.entity.adoptionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // serialized UUID
    private String petId; // serialized UUID (foreign key)
    private String userId; // serialized UUID (foreign key)
    private String requestType;
    private String status;
    private String notes;
    private Double fee;
    private String decisionBy; // serialized UUID of decision maker
    private String processedAt; // ISO timestamp
    private String requestedAt; // ISO timestamp
    private String resultDetails;

    public AdoptionJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields must be present and not blank
        if (id == null || id.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (requestType == null || requestType.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;

        // Fee should be provided and non-negative
        if (fee == null || fee < 0.0) return false;

        // Optional fields (notes, decisionBy, processedAt, resultDetails) can be null or blank
        return true;
    }
}
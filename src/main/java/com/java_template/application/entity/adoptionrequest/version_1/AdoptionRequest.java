package com.java_template.application.entity.adoptionrequest.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequest";
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on the prototype example
    private String id; // technical id (serialized UUID)
    private String ownerId; // foreign key reference (serialized UUID)
    private String petId; // foreign key reference (serialized UUID)
    private String message;
    private String status; // use String for enum-like values (e.g., "pending", "approved", "rejected")
    private String createdAt; // ISO timestamp
    private String decidedAt; // ISO timestamp, nullable
    private String decisionNote;

    public AdoptionRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    @Override
    public boolean isValid() {
        // Required fields: id, ownerId, petId, status, createdAt
        if (!notBlank(id)) return false;
        if (!notBlank(ownerId)) return false;
        if (!notBlank(petId)) return false;
        if (!notBlank(status)) return false;
        if (!notBlank(createdAt)) return false;

        // Optional fields: message, decidedAt, decisionNote - no strict checks
        return true;
    }
}
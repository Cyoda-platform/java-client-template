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
    private String id; // domain identifier
    private String petId; // reference to Pet (serialized UUID)
    private String requesterUserId; // reference to User creating request (serialized UUID)
    private String status; // REQUESTED, UNDER_REVIEW, APPROVED, REJECTED, CANCELLED, COMPLETED
    private String notes; // optional applicant notes
    private String createdAt; // ISO8601 timestamp
    private String updatedAt; // ISO8601 timestamp

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
        if (id == null || id.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (requesterUserId == null || requesterUserId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

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

    private String id; // business id
    private String petId; // linked Pet.id (serialized UUID)
    private String userId; // linked User.id (serialized UUID)
    private String status; // submitted, under_review, approved, rejected, completed
    private String submittedAt; // ISO timestamp
    private String reviewNotes; // staff notes
    private String scheduledPickup; // ISO timestamp or null

    // Optional embedded snapshot used by criteria/processors
    private com.java_template.application.entity.pet.version_1.Pet pet;

    // Additional fields expected by processors
    private Integer version;

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
        // Required fields: petId, userId, status and submittedAt
        if (petId == null || petId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (submittedAt == null || submittedAt.isBlank()) return false;
        return true;
    }

    // Compatibility convenience methods
    public String getTechnicalId() { return this.id; }
    public void setTechnicalId(String technicalId) { this.id = technicalId; }

    public com.java_template.application.entity.pet.version_1.Pet getPet() { return this.pet; }
    public void setPet(com.java_template.application.entity.pet.version_1.Pet pet) { this.pet = pet; }
}
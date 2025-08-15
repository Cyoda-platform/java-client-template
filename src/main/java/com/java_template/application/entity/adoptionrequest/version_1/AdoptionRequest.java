package com.java_template.application.entity.adoptionrequest.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Objects;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.owner.version_1.Owner;

@Data
public class AdoptionRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequest";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id
    private String petId; // foreign key to Pet.id (serialized UUID)
    private String ownerId; // foreign key to Owner.id (serialized UUID)
    private String requestDate; // ISO datetime string
    private String status; // pending/approved/rejected/cancelled
    private String notes;
    private String processedBy; // owner/staff id

    // Additional fields / aliases expected by processors
    private String technicalId;
    private String createdAt;
    private Pet pet; // optional nested pet payload
    private Owner owner; // optional nested owner payload

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
        if (ownerId == null || ownerId.isBlank()) return false;
        if (requestDate == null || requestDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }

    // Provide some convenience/compatibility accessors used by processors
    public String getTechnicalId() {
        return technicalId != null ? technicalId : id;
    }

    public void setTechnicalId(String tid) {
        this.technicalId = tid;
        if (this.id == null) this.id = tid;
    }

    public String getCreatedAt() {
        return createdAt != null ? createdAt : requestDate;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
        if (this.requestDate == null) this.requestDate = createdAt;
    }

    public String getPetTechnicalId() {
        if (pet != null && pet.getTechnicalId() != null) return pet.getTechnicalId();
        return petId;
    }

    public String getOwnerTechnicalId() {
        if (owner != null && owner.getTechnicalId() != null) return owner.getTechnicalId();
        return ownerId;
    }
}
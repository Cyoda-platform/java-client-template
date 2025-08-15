package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // domain identifier assigned by owner/system
    private String name;
    private String species; // e.g., cat, dog
    private String breed;
    private Integer ageMonths;
    private String gender; // male/female/unknown
    private String status; // DRAFT, PENDING_REVIEW, AVAILABLE, ADOPTED, ARCHIVED
    private List<String> images; // URLs or image identifiers
    private List<String> tags;
    private String description;
    private String shelterId; // reference to Shelter (serialized UUID)
    private String ownerUserId; // reference to User who listed the pet (serialized UUID)
    private String createdAt; // ISO8601 timestamp
    private String updatedAt; // ISO8601 timestamp

    public Pet() {}

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
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (ownerUserId == null || ownerUserId.isBlank()) return false;
        if (ageMonths == null || ageMonths < 0) return false;
        return true;
    }
}

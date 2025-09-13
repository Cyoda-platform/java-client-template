package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet"; 
    public static final Integer ENTITY_VERSION = 1;

    // Technical id (serialized UUID or similar)
    private String id;
    // Optional external identifier from source systems
    private String externalId;
    private String name;
    private String species;
    private String breed;
    private Integer age;
    private String status;
    private String source;
    // Foreign key reference to User (serialized UUID)
    private String ownerId;
    // ISO-8601 timestamp string
    private String createdAt;

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
        // id is treated as technical UUID-like field: ensure presence (not null)
        if (this.id == null) {
            return false;
        }
        // Required string fields: check not null/blank
        if (this.name == null || this.name.isBlank()) {
            return false;
        }
        if (this.species == null || this.species.isBlank()) {
            return false;
        }
        if (this.status == null || this.status.isBlank()) {
            return false;
        }
        if (this.source == null || this.source.isBlank()) {
            return false;
        }
        if (this.createdAt == null || this.createdAt.isBlank()) {
            return false;
        }
        // Age must be present and non-negative
        if (this.age == null || this.age < 0) {
            return false;
        }
        // ownerId is optional (can be null for unowned pets)
        return true;
    }
}
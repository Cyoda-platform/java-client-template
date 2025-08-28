package com.java_template.application.entity.owner.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Owner implements CyodaEntity {
    public static final String ENTITY_NAME = "Owner";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (serialized UUID)
    private String name;
    private String email;
    private List<String> petsOwned; // list of serialized UUIDs referencing Pet entities
    private String phone;
    private Boolean verified;

    public Owner() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank()
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        // verified must be present
        if (verified == null) return false;
        // phone is optional but if present must not be blank
        if (phone != null && phone.isBlank()) return false;
        // petsOwned is optional; if present, each entry must be a non-blank serialized id
        if (petsOwned != null) {
            for (String petId : petsOwned) {
                if (petId == null || petId.isBlank()) return false;
            }
        }
        return true;
    }
}
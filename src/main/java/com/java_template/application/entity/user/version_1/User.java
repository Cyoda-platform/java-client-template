package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (e.g., "USR-99")
    private String name;
    private String email;
    private String contact;
    private String notes;
    private String role;
    private List<String> savedPets; // serialized UUIDs / foreign key references as Strings
    private Boolean verified;

    public User() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: required string fields must not be blank.
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        // simple email sanity check
        if (!email.contains("@")) return false;
        if (role == null || role.isBlank()) return false;
        // if savedPets provided, ensure entries are non-blank strings
        if (savedPets != null) {
            for (String petId : savedPets) {
                if (petId == null || petId.isBlank()) return false;
            }
        }
        // verified can be null or boolean; no strict requirement
        return true;
    }
}
package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User"; 
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on prototype
    private String id; // serialized UUID
    private String name;
    private String email;
    private String phone;
    private String address;
    private List<String> favorites = new ArrayList<>(); // list of pet ids (serialized UUIDs)
    private String role;
    private String createdAt; // ISO timestamp as String
    private String updatedAt; // ISO timestamp as String

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
        // Validate required string fields using isBlank()
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        // basic email sanity check
        if (!email.contains("@")) return false;
        // validate favorites list elements if present
        if (favorites != null) {
            for (String fav : favorites) {
                if (fav == null || fav.isBlank()) return false;
            }
        }
        return true;
    }
}
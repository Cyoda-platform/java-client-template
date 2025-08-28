package com.java_template.application.entity.owner.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class Owner implements CyodaEntity {
    public static final String ENTITY_NAME = "Owner"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private String bio;
    private String role;
    private List<String> favoritePetIds; // serialized UUIDs as Strings
    private Instant createdAt;
    private Instant updatedAt;

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
        if (fullName == null || fullName.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (phone == null || phone.isBlank()) return false;
        if (role == null || role.isBlank()) return false;
        // Validate timestamps
        if (createdAt == null) return false;
        // updatedAt can be null for newly created entities
        return true;
    }
}
package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String userId;      // serialized id (e.g., "u-1001")
    private String name;
    private String email;
    private String role;        // use String for enum-like values (e.g., "regular")
    private Boolean subscribed;
    private String createdAt;   // ISO-8601 timestamp as String
    private String updatedAt;   // ISO-8601 timestamp as String

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
        // Validate required String fields using isBlank()
        if (userId == null || userId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (role == null || role.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        // subscribed is optional but if provided should not be null
        if (subscribed == null) return false;
        return true;
    }
}
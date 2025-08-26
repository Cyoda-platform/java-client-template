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
    private String userId;
    private String name;
    private String email;
    private String role;
    private String status;
    private String createdAt;

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
        // Required fields: userId, email, name
        if (userId == null || userId.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        // Optional fields: if provided they must not be blank
        if (role != null && role.isBlank()) return false;
        if (status != null && status.isBlank()) return false;
        if (createdAt != null && createdAt.isBlank()) return false;
        return true;
    }
}
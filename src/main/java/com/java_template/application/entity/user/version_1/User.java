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
    private String id; // UUID
    private String role; // Admin or Customer
    private String email; // unique contact and login
    private String name; // display name
    private String passwordHash; // hashed password
    private String createdAt; // ISO8601
    private Boolean active; // account enabled/disabled

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
        if (role == null || role.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (passwordHash == null || passwordHash.isBlank()) return false;
        return true;
    }
}

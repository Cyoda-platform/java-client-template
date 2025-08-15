package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;

    // Business fields
    private String id; // business id for the user
    private String role; // Admin or Customer (string, do not use enum)
    private String name; // full name
    private String email; // unique email
    private String passwordHash; // stored password hash
    private String phone; // optional phone number
    private String createdAt; // ISO8601 timestamp
    private String updatedAt; // ISO8601 timestamp

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
        if (id == null || id.isBlank()) return false;
        if (role == null || role.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (!email.contains("@")) return false;
        if (passwordHash == null || passwordHash.isBlank()) return false;
        return true;
    }
}

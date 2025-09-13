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
    private String id; // technical id (serialized UUID)
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String registeredAt; // ISO-8601 timestamp as String

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
        if (username == null || username.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        // basic email sanity check
        if (!email.contains("@")) return false;
        return true;
    }
}
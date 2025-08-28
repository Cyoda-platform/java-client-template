package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id (e.g., internal UUID serialized as String)
    private String id;

    // Business/external identifier
    private String userId;

    private String address;
    private List<String> adoptedPetIds;
    private String email;
    private String fullName;
    private String phone;
    private Map<String, Object> preferences;
    private String registeredAt; // ISO-8601 timestamp as String
    private String status;

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
        // Validate required string fields using isBlank semantics
        if (userId == null || userId.isBlank()) return false;
        if (fullName == null || fullName.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (registeredAt == null || registeredAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // adoptedPetIds and preferences are optional but must not be explicitly invalid (null allowed)
        return true;
    }
}
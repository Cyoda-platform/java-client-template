package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;

public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User"; 
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on prototype
    private String id; // technical id (serialized UUID or string)
    private String name;
    private String email;
    private String identificationStatus; // enum represented as String (e.g., "ANON")
    private String createdAt; // ISO-8601 timestamp as String
    private String updatedAt; // ISO-8601 timestamp as String

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
        // Validate required string fields are present and not blank
        if (id == null || id.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (identificationStatus == null || identificationStatus.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (updatedAt == null || updatedAt.isBlank()) return false;
        return true;
    }

    // getters/setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getIdentificationStatus() { return identificationStatus; }
    public void setIdentificationStatus(String identificationStatus) { this.identificationStatus = identificationStatus; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
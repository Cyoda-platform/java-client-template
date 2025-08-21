package com.java_template.application.entity.owner.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

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
    private List<String> preferences; // e.g., ["Cat"]
    private List<String> savedPets; // serialized pet ids, e.g., ["PET-123"]
    private String verificationStatus;
    private String createdAt; // ISO-8601 timestamp string
    private String updatedAt; // ISO-8601 timestamp string

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
        // Basic validation: id, fullName and email must be present and not blank
        if (id == null || id.isBlank()) return false;
        if (fullName == null || fullName.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        return true;
    }
}
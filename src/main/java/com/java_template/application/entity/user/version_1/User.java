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
    // Entity fields derived from example
    private String id;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private String createdAt; // ISO-8601 timestamp as String
    // Foreign-key references (serialized UUIDs) and simple lists
    private List<String> adoptionHistory = new ArrayList<>();
    private List<String> preferences = new ArrayList<>();

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
        // Basic validation: required string fields must be present and not blank
        if (id == null || id.isBlank()) return false;
        if (fullName == null || fullName.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (!email.contains("@")) return false; // simple email sanity check
        if (createdAt == null || createdAt.isBlank()) return false;

        // Validate lists: elements, if present, must not be blank
        if (adoptionHistory != null) {
            for (String a : adoptionHistory) {
                if (a == null || a.isBlank()) return false;
            }
        }
        if (preferences != null) {
            for (String p : preferences) {
                if (p == null || p.isBlank()) return false;
            }
        }
        return true;
    }
}
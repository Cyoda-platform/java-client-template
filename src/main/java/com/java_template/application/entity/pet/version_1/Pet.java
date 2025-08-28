package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // technical id (serialized UUID) returned by POST endpoints
    private String id;

    // entity fields based on prototype
    private Integer age;
    private String breed;
    private String createdAt; // ISO-8601 timestamp as String
    private List<String> healthRecords = new ArrayList<>();
    private List<String> images = new ArrayList<>();
    private Map<String, Object> metadata = new HashMap<>();
    private String name;
    private String petId; // external id (serialized)
    private String source;
    private String species;
    private String status;

    public Pet() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields: use isBlank checks
        if (name == null || name.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // createdAt if provided must be non-blank
        if (createdAt != null && createdAt.isBlank()) return false;

        // Numeric constraints
        if (age != null && age < 0) return false;

        // Collections should not be null
        if (healthRecords == null) return false;
        if (images == null) return false;
        if (metadata == null) return false;

        return true;
    }
}
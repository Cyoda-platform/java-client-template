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
    private String name;
    private String email;
    private String phone;
    private String address;
    private List<String> favorites;
    private List<String> petsOwned; // foreign keys (serialized UUIDs) referencing Pet entities
    private String registeredAt; // ISO-8601 timestamp as String

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
        // Validate required string fields using isBlank()
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (phone == null || phone.isBlank()) return false;
        if (registeredAt == null || registeredAt.isBlank()) return false;
        // lists (favorites, petsOwned) may be null or empty, that's acceptable
        return true;
    }
}
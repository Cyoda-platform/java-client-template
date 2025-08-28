package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id, e.g., "PET-123"
    private String name;
    private Integer age;
    private String breed;
    private String description;
    private List<String> healthNotes;
    private List<String> photos;
    private String sex; // use String for enums
    private String species;
    private String status;
    private String createdAt; // ISO-8601 serialized datetime
    private String updatedAt; // ISO-8601 serialized datetime

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
        // Required string fields must be present and not blank
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (sex == null || sex.isBlank()) return false;
        // age if provided must be non-negative
        if (age != null && age < 0) return false;
        return true;
    }
}
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
    private String id;
    private String externalId;
    private String name;
    private String description;
    private String species;
    private String breed;
    private String color;
    private String sex;
    private String size;
    private String source;
    private String status;
    private Integer ageMonths;
    private String arrivalDate; // ISO date string
    private String createdAt; // ISO datetime string
    private String updatedAt; // ISO datetime string
    private String adoptedByUserId; // serialized UUID reference
    private List<String> photos;
    private List<String> healthRecords;

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
        // Required string fields must not be blank
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Numeric fields validation
        if (ageMonths == null || ageMonths < 0) return false;

        // Collections should be non-null (can be empty)
        if (photos == null) return false;
        if (healthRecords == null) return false;

        // arrivalDate, createdAt, updatedAt and adoptedByUserId can be null/blank depending on lifecycle
        return true;
    }
}
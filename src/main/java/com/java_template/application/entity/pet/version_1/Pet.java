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

    // Technical internal id (e.g., serialized UUID). Returned by POST endpoints.
    private String technicalId;

    // External/source id from provider (e.g., "pet-source-123")
    private String id;

    private String name;
    private Integer age;
    private String breed;
    private String species;
    private String gender;
    private String description;
    private String healthNotes;
    private String location;
    private List<String> photos;
    private String source;
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
        // Required string fields must be non-blank
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Age, if provided, must be non-negative
        if (age != null && age < 0) return false;

        // Photos, if provided, must not contain blank entries
        if (photos != null) {
            for (String p : photos) {
                if (p == null || p.isBlank()) return false;
            }
        }

        // Optional fields may be blank or null (breed, gender, description, healthNotes, location, source, id, technicalId)
        return true;
    }
}
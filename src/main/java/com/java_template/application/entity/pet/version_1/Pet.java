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
    private String id; // technical id (e.g., "PET-123")
    private String name;
    private Integer age;
    private String breed;
    private String description;
    private String gender;
    private String healthNotes;
    private List<String> photos;
    private String species;
    private String status;
    private List<String> tags;

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
        // Basic required fields
        if (id == null || id.isBlank()) {
            return false;
        }
        if (name == null || name.isBlank()) {
            return false;
        }
        if (species == null || species.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }

        // Age, if provided, must be non-negative
        if (age != null && age < 0) {
            return false;
        }

        // Validate lists if present: no blank entries
        if (photos != null) {
            for (String p : photos) {
                if (p == null || p.isBlank()) {
                    return false;
                }
            }
        }
        if (tags != null) {
            for (String t : tags) {
                if (t == null || t.isBlank()) {
                    return false;
                }
            }
        }

        // Optional string fields - if present, they should not be blank
        if (breed != null && breed.isBlank()) {
            return false;
        }
        if (description != null && description.isBlank()) {
            return false;
        }
        if (gender != null && gender.isBlank()) {
            return false;
        }
        if (healthNotes != null && healthNotes.isBlank()) {
            return false;
        }

        return true;
    }
}
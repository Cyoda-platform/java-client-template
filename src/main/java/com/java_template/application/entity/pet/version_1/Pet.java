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

    // Entity fields based on prototype
    private String id; // serialized id (technical id)
    private String sourceId; // serialized external source id
    private String name;
    private String description;
    private Integer age;
    private String breed;
    private String species; // use String for enum-like values
    private String status; // use String for enum-like values
    private List<String> images;
    private List<String> tags;
    private String createdAt; // ISO timestamp as String
    private String updatedAt; // ISO timestamp as String
    private String sourceUpdatedAt; // ISO timestamp as String

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
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (updatedAt == null || updatedAt.isBlank()) return false;

        // Optional string fields, if present, must not be blank
        if (sourceId != null && sourceId.isBlank()) return false;
        if (sourceUpdatedAt != null && sourceUpdatedAt.isBlank()) return false;
        if (description != null && description.isBlank()) return false;
        if (breed != null && breed.isBlank()) return false;

        // Age, if present, must be non-negative
        if (age != null && age < 0) return false;

        // Collections, if present, should not contain blank entries
        if (images != null) {
            for (String img : images) {
                if (img == null || img.isBlank()) return false;
            }
        }
        if (tags != null) {
            for (String tag : tags) {
                if (tag == null || tag.isBlank()) return false;
            }
        }

        return true;
    }
}
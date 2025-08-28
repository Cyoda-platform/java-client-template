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
    // Technical/primary id (serialized UUID or string identifier)
    private String petId;

    private String name;
    private String species; // use String for enum-like values
    private String breed;
    private Integer age;
    private String gender;
    private String importedFrom;
    private String description;
    private List<String> photoUrls;
    private String status; // use String for enum-like values
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
        // Required string fields must not be blank
        if (petId == null || petId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Age, if provided, must be non-negative
        if (age != null && age < 0) return false;

        // photoUrls and tags should be non-null collections (can be empty), and should not contain blank entries
        if (photoUrls == null) return false;
        for (String url : photoUrls) {
            if (url == null || url.isBlank()) return false;
        }

        if (tags == null) return false;
        for (String t : tags) {
            if (t == null || t.isBlank()) return false;
        }

        // optional fields (breed, description, gender, importedFrom) may be null/blank
        return true;
    }
}
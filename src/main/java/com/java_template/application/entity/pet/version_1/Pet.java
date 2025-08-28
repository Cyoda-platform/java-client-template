package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (serialized UUID or similar)
    private String name;
    private String species;
    private String breed;
    private String sex;
    private Integer ageMonths;
    private String description;
    private String status;
    private String mood;
    private String origin;
    private String imageUrl;
    private String addedAt; // ISO-8601 timestamp as String
    private List<String> tags = new ArrayList<>();

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
        // Required string fields: id, name, species, status
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Optional strings must not be blank if provided
        if (breed != null && breed.isBlank()) return false;
        if (sex != null && sex.isBlank()) return false;
        if (description != null && description.isBlank()) return false;
        if (mood != null && mood.isBlank()) return false;
        if (origin != null && origin.isBlank()) return false;
        if (imageUrl != null && imageUrl.isBlank()) return false;
        if (addedAt != null && addedAt.isBlank()) return false;

        // Numeric validation
        if (ageMonths != null && ageMonths < 0) return false;

        // Tags validation
        if (tags != null) {
            for (String t : tags) {
                if (t == null || t.isBlank()) return false;
            }
        }

        return true;
    }
}
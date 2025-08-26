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

    // Entity fields based on requirements prototype
    private String id; // technical id (serialized UUID or string id)
    private String name;
    private Integer age;
    private String breed;
    private String description;
    private List<String> photos = new ArrayList<>();
    private String sourceId; // foreign key reference (serialized UUID as String)
    private String sourceUrl;
    private String species;
    private String status

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

        // age, if provided, must be non-negative
        if (age != null && age < 0) return false;

        // photos list must not contain blank entries
        if (photos != null) {
            for (String p : photos) {
                if (p == null || p.isBlank()) return false;
            }
        }

        // sourceId and sourceUrl are optional; if provided they must not be blank
        if (sourceId != null && sourceId.isBlank()) return false;
        if (sourceUrl != null && sourceUrl.isBlank()) return false;

        // breed and description are optional; if provided they must not be blank
        if (breed != null && breed.isBlank()) return false;
        if (description != null && description.isBlank()) return false;

        return true;
    }
}
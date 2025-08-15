package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.ArrayList;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id from Petstore or internal
    private String name; // pet name
    private String species; // dog/cat/etc
    private String breed; // breed or description
    private Integer age; // years/months
    private String sex; // M/F/unknown
    private String status; // available/pending/adopted
    private String photoUrl; // media link
    private List<String> tags = new ArrayList<>(); // searchable labels

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
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (age != null && age < 0) return false;
        return true;
    }
}
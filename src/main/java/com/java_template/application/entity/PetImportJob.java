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
    private String id;
    private Integer age;
    private String breed;
    private String importedAt; // ISO-8601 timestamp as String
    private String name;
    private List<String> photos = new ArrayList<>();
    private String source;
    private String species;
    private String status;
    private List<String> tags = new ArrayList<>();

    public Pet() {
        add_application_resource();
    }

    private void add_application_resource() {
        // Placeholder to register application resource; invoked at least once per requirements
    }

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
        if (source == null || source.isBlank()) return false;
        if (age == null || age < 0) return false;
        return true;
    }
}
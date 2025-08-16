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

    private String petId; // external Petstore id
    private String technicalId; // internal technical id used by workflow
    private String name;
    private String species; // cat/dog/etc
    private String breed;
    private Integer age; // years/months
    private String gender;
    private String description;
    private List<String> tags;
    private List<String> images; // urls
    private String status; // available pending adopted
    private String source; // Petstore/local
    private String createdAt; // datetime as String

    public Pet() {
        this.tags = new ArrayList<>();
        this.images = new ArrayList<>();
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
        if (petId == null || petId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        return true;
    }
}

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
    private String id; // business id
    private String name; // pet name
    private String species; // cat/dog/etc
    private String breed; // breed info
    private Integer age; // years
    private String gender; // M/F/unknown
    private List<String> photos = new ArrayList<>(); // urls
    private String status; // available/requested/under_review/adopted/returned
    private List<String> tags = new ArrayList<>(); // search tags
    private String description; // free text

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

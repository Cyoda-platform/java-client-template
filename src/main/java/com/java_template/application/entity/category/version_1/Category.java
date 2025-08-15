package com.java_template.application.entity.category.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Category implements CyodaEntity {
    public static final String ENTITY_NAME = "Category";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // domain identifier
    private String name; // category/tag name
    private String description; // optional description
    private String createdAt; // ISO8601 timestamp
    private String updatedAt; // ISO8601 timestamp

    public Category() {}

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
        return true;
    }
}

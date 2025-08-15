package com.java_template.application.entity.review.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Review implements CyodaEntity {
    public static final String ENTITY_NAME = "Review";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // domain identifier
    private String petId; // reference to Pet (serialized UUID)
    private String userId; // reference to User (serialized UUID)
    private Integer rating; // 1-5 scale
    private String comment; // text
    private String createdAt; // ISO8601 timestamp

    public Review() {}

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
        if (petId == null || petId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (rating == null || rating < 1 || rating > 5) return false;
        return true;
    }
}

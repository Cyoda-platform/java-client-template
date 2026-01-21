package com.example.application.entity.environment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.OffsetDateTime;

/**
 * Environment Entity - Represents an environment where a user has access
 * 
 * This entity tracks user access to different environments with audit information.
 */
@Data
public class Environment implements CyodaEntity {
    public static final String ENTITY_NAME = "Environment";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - combination of userId and environment name
    private String environmentId;

    // Reference to user
    private String userId;

    // Environment details
    private String name;
    private OffsetDateTime lastAccessTime;
    private Boolean markedForDeletion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return environmentId != null && !environmentId.isBlank() && 
               userId != null && !userId.isBlank();
    }
}


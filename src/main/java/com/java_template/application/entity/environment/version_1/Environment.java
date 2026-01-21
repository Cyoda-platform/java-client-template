package com.java_template.application.entity.environment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.OffsetDateTime;

/**
 * Environment Entity - Represents a user's environment access record
 * 
 * This entity tracks environment information including access time
 * and deletion status for user environment management.
 */
@Data
public class Environment implements CyodaEntity {
    public static final String ENTITY_NAME = "Environment";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String name;

    // Environment access tracking
    private OffsetDateTime lastAccessTime;
    private Boolean markedForDeletion;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields
        return name != null && !name.isBlank();
    }
}


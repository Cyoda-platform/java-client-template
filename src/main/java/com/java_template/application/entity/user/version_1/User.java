package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * User Entity - Represents a user account synchronized from Auth0
 * 
 * This entity manages user information including Auth0 identity,
 * local metadata, and environment access tracking.
 */
@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String id;

    // Auth0 identity fields
    private String auth0Id;
    private String email;
    private String name;

    // Local metadata from Auth0 user_metadata
    private Map<String, Object> metadata;

    // Local-only fields
    private Boolean presentInAuth0;
    private List<Environment> environments;

    // Audit fields
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
        // Validate required fields
        return id != null && !id.isBlank() && email != null && !email.isBlank();
    }

    /**
     * Nested class for environment access information
     */
    @Data
    public static class Environment {
        private String name;
        private OffsetDateTime lastAccessTime;
        private Boolean markedForDeletion;
    }
}


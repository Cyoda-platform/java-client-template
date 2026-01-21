package com.example.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * User Entity - Represents a user synchronized from Auth0
 * 
 * This entity manages user accounts with Auth0 metadata and local-only fields.
 * Auth0 is authoritative for shared fields, while local-only fields are preserved.
 */
@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - Auth0 user ID
    private String userId;

    // Auth0 Profile Fields
    private String email;
    private String name;
    private String nickname;
    private String picture;
    private String locale;
    private String zoneinfo;
    private String gender;
    private String birthdate;
    private String phoneNumber;
    private String updatedAt;

    // Auth0 Metadata (optional fields from user_metadata)
    private String caasUserId;
    private Boolean caasCyodaEmployee;
    private String caasTier;

    // Local-only Fields
    private Boolean presentInAuth0;
    private List<Environment> environments;
    private OffsetDateTime lastSyncTime;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return userId != null && !userId.isBlank();
    }

    /**
     * Nested class for environment information
     */
    @Data
    public static class Environment {
        private String name;
        private OffsetDateTime lastAccessTime;
        private Boolean markedForDeletion;
    }
}


package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;

    // Technical identifier (serialized UUID)
    private String userId;

    // References to audits (serialized UUIDs)
    private List<String> auditRefs;

    // Contact
    private String email;
    private Boolean emailVerified;

    // GDPR state (use String for enum-like values)
    private String gdprState;

    // Flattened marketing flags
    private Boolean marketingEnabled;

    // References to posts owned by this user (serialized UUIDs)
    private List<String> ownerOfPosts;

    // Profile sub-object
    private Profile profile;

    public User() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // userId is a technical UUID reference -> ensure it's present (not null)
        if (this.userId == null) {
            return false;
        }
        // email is a regular string -> ensure not blank
        if (this.email == null || this.email.isBlank()) {
            return false;
        }
        // if profile present, validate inner fields
        if (this.profile != null) {
            if (this.profile.getName() == null || this.profile.getName().isBlank()) {
                return false;
            }
        }
        return true;
    }

    @Data
    public static class Profile {
        private String name;
        private String bio;
        private String locale;
    }
}
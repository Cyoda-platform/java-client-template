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
    // Add your entity fields here
    private List<String> auditRefs;
    private String email;
    private Boolean emailVerified;
    private String gdprState;
    private Boolean marketingEnabled;
    private List<String> ownerOfPosts; // foreign key references (serialized UUIDs) to posts
    private Profile profile;
    private String userId;

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
        // userId is required and must not be blank
        if (userId == null || userId.isBlank()) return false;
        // email is required and must look like an email
        if (email == null || email.isBlank()) return false;
        if (!email.contains("@")) return false;

        // validate lists if present: elements must not be blank
        if (auditRefs != null) {
            for (String ref : auditRefs) {
                if (ref == null || ref.isBlank()) return false;
            }
        }
        if (ownerOfPosts != null) {
            for (String ref : ownerOfPosts) {
                if (ref == null || ref.isBlank()) return false;
            }
        }

        // validate profile if present
        if (profile != null) {
            if (profile.getName() == null || profile.getName().isBlank()) return false;
            if (profile.getLocale() != null && profile.getLocale().isBlank()) return false;
            // bio can be empty
        }

        return true;
    }

    @Data
    public static class Profile {
        private String bio;
        private String locale;
        private String name;
    }
}
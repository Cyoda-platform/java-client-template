package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String technicalId;
    private String userId;
    private String role;
    private String email;
    private Map<String, Object> preferences;
    private List<String> favorites;
    private Map<String, Object> notificationPreferences;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastActiveAt;
    private String status;

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
        if (this.userId == null || this.userId.isBlank()) {
            return false;
        }
        if (this.email == null || this.email.isBlank()) {
            return false;
        }
        if (this.role == null || this.role.isBlank()) {
            return false;
        }
        return true;
    }
}
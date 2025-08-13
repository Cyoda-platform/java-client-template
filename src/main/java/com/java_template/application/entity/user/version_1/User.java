package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;

    private String userId;
    private String name;
    private String email;
    private String role;
    private LocalDateTime createdAt;

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
        return userId != null && !userId.isBlank()
            && name != null && !name.isBlank()
            && email != null && !email.isBlank()
            && role != null && !role.isBlank()
            && createdAt != null;
    }
}

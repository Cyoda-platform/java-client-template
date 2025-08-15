package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;
import java.util.HashMap;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;

    // Fields
    private String id; // UUID as string
    private String email;
    private String fullName;
    private String role; // Admin|Customer
    private String status; // Active|Inactive
    private String createdAt; // ISO8601
    private String updatedAt; // ISO8601
    private String importSource;
    private String importBatchId;
    private Map<String, Object> importMetadata = new HashMap<>();

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
        return id != null && !id.isBlank()
            && email != null && !email.isBlank();
    }
}

package com.java_template.application.entity.user.version_1; // replace {entityName} with actual entity name in lowercase

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

    private String id; // business id
    private String name;
    private String email;
    private String contact;
    private String role; // customer/admin/staff
    private List<String> favorites; // pet ids

    // Additional fields expected by processors
    private String lifecycleState;
    private String updatedAt;

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
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (role == null || role.isBlank()) return false;
        if (favorites == null) return false;
        return true;
    }

    // Compatibility helpers used by processors
    public String getTechnicalId() {
        return this.id;
    }

    public void setTechnicalId(String technicalId) {
        this.id = technicalId;
    }

    // lifecycleState and updatedAt have Lombok-generated getters/setters because of @Data
}

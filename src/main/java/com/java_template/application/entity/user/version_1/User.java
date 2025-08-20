package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;

    private String userId; // business id
    private String email; // contact/login
    private String name; // display name
    private String status; // identity state e.g. ANONYMOUS REGISTERED VERIFIED LOGGED_IN

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
        if (userId == null || userId.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // simple email format check
        if (!email.contains("@")) return false;
        return true;
    }
}

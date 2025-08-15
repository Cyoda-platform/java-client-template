package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id, e.g., external CRM id
    private String email; // contact and login
    private String name; // full name
    private String role; // Admin or Customer (string)
    private String status; // active suspended pending_validation
    private String importedFrom; // ImportJob id or manual (serialized UUID)
    private String createdAt; // DateTime as ISO string

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
        if (email == null || email.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        return true;
    }
}

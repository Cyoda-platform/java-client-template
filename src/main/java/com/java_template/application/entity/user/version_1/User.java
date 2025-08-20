package com.java_template.application.entity.user.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String userId; // external user id from Fakerest (serialized UUID)
    private String name; // display name
    private String email; // contact
    private String status; // active/inactive
    private String createdAt; // ISO timestamp
    private String lastSeenAt; // ISO timestamp

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
        // require userId and email
        if (this.userId == null || this.userId.isBlank()) return false;
        if (this.email == null || this.email.isBlank()) return false;
        return true;
    }
}

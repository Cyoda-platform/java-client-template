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

    private Integer id; // id returned by ReqRes
    private String email; // user email
    private String first_name; // user first name
    private String last_name; // user last name
    private String avatar; // avatar URL
    private String retrievedAt; // ISO8601 timestamp

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
        // Validate required fields: id must be positive, email/first_name/last_name should not be blank, retrievedAt should not be blank
        if (this.id == null || this.id <= 0) return false;
        if (this.email == null || this.email.isBlank()) return false;
        if (this.first_name == null || this.first_name.isBlank()) return false;
        if (this.last_name == null || this.last_name.isBlank()) return false;
        if (this.retrievedAt == null || this.retrievedAt.isBlank()) return false;
        return true;
    }
}

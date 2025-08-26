package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.Instant;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private Integer id; // technical id
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private String validationStatus; // use String for enum-like values
    private Instant sourceFetchedAt;
    private Instant transformedAt;
    private Instant storedAt;

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
        // Validate required string fields using isBlank()
        if (username == null || username.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (validationStatus == null || validationStatus.isBlank()) return false;
        // Timestamps and id are optional for creation; additional checks can be added if required
        return true;
    }
}
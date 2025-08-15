package com.java_template.application.entity.user.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.ArrayList;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String billingAddress;
    private String shippingAddress;
    private String passwordHash;
    private List<String> roles = new ArrayList<>(); // Admin or Customer
    private String createdAt;
    private Boolean isActive;

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
        if (roles == null || roles.isEmpty()) return false;
        return true;
    }
}

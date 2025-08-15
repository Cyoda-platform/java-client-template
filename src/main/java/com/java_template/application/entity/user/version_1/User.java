package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // business id
    private String name;
    private String email;
    private String phone;
    private String address;
    private String role; // customer/staff
    private List<String> favorites = new ArrayList<>(); // pet ids (serialized UUIDs)

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
        return true;
    }
}

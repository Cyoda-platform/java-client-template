package com.java_template.application.entity.owner.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Owner implements CyodaEntity {
    public static final String ENTITY_NAME = "Owner"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (serialized UUID or string id)
    private String name;
    private String address;
    private String phone;
    private String contactEmail;
    private String preferences; // JSON string with owner preferences

    public Owner() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // id and name are required
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        // contactEmail if present should not be blank
        if (contactEmail != null && contactEmail.isBlank()) return false;
        return true;
    }
}
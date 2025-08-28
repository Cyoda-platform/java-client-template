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
    private String id; // technical id (serialized UUID or string identifier)
    private String name;
    private String address;
    private String contactEmail;
    private String contactPhone;
    private String preferences;

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
        // Required fields
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;

        // Optional fields: if present they must not be blank
        if (address != null && address.isBlank()) return false;
        if (contactEmail != null && contactEmail.isBlank()) return false;
        if (contactPhone != null && contactPhone.isBlank()) return false;
        if (preferences != null && preferences.isBlank()) return false;

        return true;
    }
}
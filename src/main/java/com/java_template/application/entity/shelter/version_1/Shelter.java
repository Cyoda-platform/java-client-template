package com.java_template.application.entity.shelter.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Shelter implements CyodaEntity {
    public static final String ENTITY_NAME = "Shelter";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // domain identifier
    private String name; // shelter or rescue name
    private String location; // address or coordinates
    private String contactEmail;
    private String contactPhone;
    private String createdAt; // ISO8601 timestamp
    private String updatedAt; // ISO8601 timestamp

    public Shelter() {}

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
        if (contactEmail == null || contactEmail.isBlank()) return false;
        return true;
    }
}

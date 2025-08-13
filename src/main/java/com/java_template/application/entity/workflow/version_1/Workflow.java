package com.java_template.application.entity.workflow.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Workflow implements CyodaEntity {
    public static final String ENTITY_NAME = "Workflow";
    public static final Integer ENTITY_VERSION = 1;

    private String name;
    private String description;
    private String status;
    private String createdAt;
    private String inputPetData;

    public Workflow() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (inputPetData == null || inputPetData.isBlank()) {
            return false;
        }
        return true;
    }
}}
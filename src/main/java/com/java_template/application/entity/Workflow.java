package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Workflow implements CyodaEntity {

    public static final String ENTITY_NAME = "Workflow";

    private String name;
    private String createdAt;
    private String status;
    private String petCategory;
    private String petStatus;

    public Workflow() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (name == null || name.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (petCategory == null || petCategory.isBlank()) return false;
        if (petStatus == null || petStatus.isBlank()) return false;
        return true;
    }
}
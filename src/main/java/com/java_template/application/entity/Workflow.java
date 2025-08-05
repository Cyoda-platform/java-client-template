package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Workflow implements CyodaEntity {
    public static final String ENTITY_NAME = "Workflow";
    
    private String workflowName;
    private String description;
    private String status;
    private String petCriteria;
    private String createdAt;

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
        if (workflowName == null || workflowName.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (petCriteria == null || petCriteria.isBlank()) return false;
        return true;
    }
}

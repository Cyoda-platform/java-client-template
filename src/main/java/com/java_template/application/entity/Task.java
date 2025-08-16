package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Task implements CyodaEntity {
    public static final String ENTITY_NAME = "Task";

    private String workflowTechnicalId;
    private String title;
    private String detail;
    private String status;
    private String createdAt;

    public Task() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return workflowTechnicalId != null && !workflowTechnicalId.isBlank()
            && title != null && !title.isBlank()
            && status != null && !status.isBlank();
    }
}

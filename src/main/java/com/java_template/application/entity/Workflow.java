package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Workflow implements CyodaEntity {
    
    private String workflowName;
    private String triggerType;
    private String status;

    public Workflow() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("workflow");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "workflow");
    }

    @Override
    public boolean isValid() {
        return !(workflowName == null || workflowName.isBlank())
                && !(triggerType == null || triggerType.isBlank())
                && !(status == null || status.isBlank());
    }
}

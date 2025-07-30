package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class ReportEmail implements CyodaEntity {
    public static final String ENTITY_NAME = "ReportEmail";

    private String workflowTechnicalId;
    private String emailTo;
    private String emailContent;
    private String status;
    private String timestamp;

    public ReportEmail() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (workflowTechnicalId == null || workflowTechnicalId.isBlank()) {
            return false;
        }
        if (emailTo == null || emailTo.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        return true;
    }
}

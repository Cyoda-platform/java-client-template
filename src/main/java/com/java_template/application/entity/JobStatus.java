package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.Instant;

@Data
public class JobStatus implements CyodaEntity {
    private String status;
    private Instant timestamp;

    public JobStatus() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("jobStatus");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "jobStatus");
    }

    @Override
    public boolean isValid() {
        return status != null && !status.isEmpty() && timestamp != null;
    }
}

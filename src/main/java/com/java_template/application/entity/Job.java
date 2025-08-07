package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";

    private String jobId;
    private String status;
    private String scheduledAt;
    private String startedAt;
    private String finishedAt;
    private String resultSummary;

    public Job() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return jobId != null && !jobId.isBlank()
                && status != null && !status.isBlank()
                && scheduledAt != null && !scheduledAt.isBlank();
    }
}

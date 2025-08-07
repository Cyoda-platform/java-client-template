package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";

    private String jobName;
    private String status;  // Possible values: SCHEDULED, INGESTING, SUCCEEDED, FAILED, NOTIFIED_SUBSCRIBERS
    private String createdAt;
    private String finishedAt;  // Nullable if job is in progress
    private String details;  // Optional details or logs related to the job

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
        return jobName != null && !jobName.isBlank() &&
               status != null && !status.isBlank() &&
               createdAt != null && !createdAt.isBlank();
    }
}

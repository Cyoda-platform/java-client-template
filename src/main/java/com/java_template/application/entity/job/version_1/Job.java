package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;

    private String jobName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;

    public Job() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (jobName == null || jobName.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // createdAt, startedAt, finishedAt can be null initially
        // errorMessage can be null
        return true;
    }
}

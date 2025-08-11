package com.java_template.application.entity.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;

    private String jobId;
    private String status;
    private String createdAt;
    private String startedAt;
    private String completedAt;
    private String errorMessage;
    private String parameters;

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
        // jobId and status should not be blank
        if (jobId == null || jobId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // createdAt should not be blank
        if (createdAt == null || createdAt.isBlank()) return false;
        // startedAt, completedAt, errorMessage and parameters can be null or blank
        return true;
    }
}

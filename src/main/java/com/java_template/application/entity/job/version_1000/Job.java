package com.java_template.application.entity.job.version_1000;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1000;

    private String jobName;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
    private String errorDetails;

    public Job() {}

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        if (jobName == null || jobName.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null) return false;
        return true;
    }
}

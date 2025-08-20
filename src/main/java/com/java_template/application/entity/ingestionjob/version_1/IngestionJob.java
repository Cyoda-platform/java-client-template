package com.java_template.application.entity.ingestionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class IngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionJob";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String technicalId;
    private String jobName;
    private OffsetDateTime scheduledFor;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private Integer fetchedCount;
    private Integer newCount;
    private Integer duplicateCount;
    private String errorSummary;
    private String status;
    private String initiatedBy;
    private Map<String, Object> runParameters;
    private OffsetDateTime createdAt;

    public IngestionJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (this.jobName == null || this.jobName.isBlank()) {
            return false;
        }
        if (this.scheduledFor == null) {
            return false;
        }
        return true;
    }
}
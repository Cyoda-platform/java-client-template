package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id;
    private String jobType; // INGEST_PETSTORE_DATA, SEND_NOTIFICATIONS, CLEANUP, SYNC_SHELTERS
    private Map<String, Object> parameters;
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    private String startedAt; // ISO8601 timestamp
    private String finishedAt; // ISO8601 timestamp
    private String resultSummary;

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
        // jobType and status are required
        if (jobType == null || jobType.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

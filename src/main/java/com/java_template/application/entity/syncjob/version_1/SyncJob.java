package com.java_template.application.entity.syncjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SyncJob implements CyodaEntity {
    public static final String ENTITY_NAME = "SyncJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String jobName; // human-friendly name for the sync job
    private String sourceUrl; // e.g., Petstore API base URL or OpenAPI endpoint
    private String sourceType; // e.g., PetstoreAPI
    private Map<String, Object> jobParameters; // free-form parameters: filters, maxRecords, concurrency, etc.
    private String scheduleCron; // optional cron expression if scheduled
    private String status; // PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED (use String, not enum)
    private String startedAt; // ISO-8601
    private String finishedAt; // ISO-8601
    private Integer processedCount;
    private Integer persistedCount;
    private Integer failedCount;
    private List<String> errors; // human readable error messages
    private String createdAt; // ISO-8601
    private String updatedAt; // ISO-8601

    public SyncJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: jobName and sourceUrl are required
        if(this.jobName == null || this.jobName.isBlank()) return false;
        if(this.sourceUrl == null || this.sourceUrl.isBlank()) return false;
        return true;
    }
}

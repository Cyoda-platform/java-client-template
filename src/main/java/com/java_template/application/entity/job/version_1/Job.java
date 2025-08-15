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

    private String id; // business job identifier, e.g., ingest-2025-08-01
    private String name; // human-friendly name of the job
    private String schedule; // cron expression or human schedule descriptor
    private String sourceEndpoint; // data source endpoint
    private Map<String,Object> parameters; // additional parameters for ingestion
    private String status; // lifecycle state: SCHEDULED, INGESTING, ...
    private String createdAt; // ISO-8601 timestamp
    private String startedAt; // ISO-8601 timestamp
    private String completedAt; // ISO-8601 timestamp
    private Integer processedRecordsCount; // number of laureates processed
    private String lastError; // last error message/stack trace
    private Integer attemptCount; // retry attempts made
    private Integer maxAttempts; // maximum retry attempts
    private Map<String,Object> subscriberFilters; // filters to select target subscribers

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
        // Basic validation: required fields must be present and not blank
        if (this.id == null || this.id.isBlank()) return false;
        if (this.name == null || this.name.isBlank()) return false;
        if (this.schedule == null || this.schedule.isBlank()) return false;
        if (this.sourceEndpoint == null || this.sourceEndpoint.isBlank()) return false;
        if (this.maxAttempts == null || this.maxAttempts < 0) return false;
        return true;
    }
}

package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String jobId;        // logical job identifier (e.g., "daily-nobel-ingest")
    private String scheduledAt;  // ISO-8601 timestamp string when job was scheduled
    private String startedAt;    // ISO-8601 timestamp string when job started
    private String finishedAt;   // ISO-8601 timestamp string when job finished
    private String sourceUrl;    // source URL used by the job
    private String status;       // job status (use String for enum-like values)
    private String summary;      // optional summary message (e.g., "ingested 5 laureates")

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
        // Validate required string fields using isBlank()
        if (jobId == null || jobId.isBlank()) return false;
        if (scheduledAt == null || scheduledAt.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // summary, startedAt, finishedAt can be optional
        return true;
    }
}
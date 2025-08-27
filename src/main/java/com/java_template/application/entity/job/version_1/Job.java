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
    // Identifier for the job (e.g., "daily_nobel_ingest")
    private String jobId;
    // Cron or schedule expression (e.g., "0 0 2 * * ?")
    private String schedule;
    // Source endpoint for the job (e.g., dataset URL)
    private String sourceEndpoint;
    // Job state as string (e.g., "SUCCEEDED", "FAILED", "RUNNING")
    private String state;
    // Timestamps as ISO strings
    private String triggeredAt;
    private String startedAt;
    private String finishedAt;
    // Summary of results
    private String resultSummary;
    // Error details if any
    private String errorDetails;
    // Number of retries attempted
    private Integer retryCount;

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
        // jobId and state are required and must not be blank
        if (jobId == null || jobId.isBlank()) return false;
        if (state == null || state.isBlank()) return false;
        // retryCount must be present and non-negative
        if (retryCount == null || retryCount < 0) return false;
        // If provided, string fields must not be blank
        if (schedule != null && schedule.isBlank()) return false;
        if (sourceEndpoint != null && sourceEndpoint.isBlank()) return false;
        if (triggeredAt != null && triggeredAt.isBlank()) return false;
        if (startedAt != null && startedAt.isBlank()) return false;
        if (finishedAt != null && finishedAt.isBlank()) return false;
        if (resultSummary != null && resultSummary.isBlank()) return false;
        if (errorDetails != null && errorDetails.isBlank()) return false;
        return true;
    }
}
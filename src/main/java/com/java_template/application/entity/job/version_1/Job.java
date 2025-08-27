package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id for the job (serialized UUID or string id)
    private String jobId;

    // Timestamps in ISO-8601 format
    private String createdAt;
    private String startedAt;
    private String finishedAt;

    // Job configuration / metadata
    private String sourceUrl;
    private String schedule;   // use String for enum-like values (e.g., "ON_DEMAND")
    private String notifyOn;   // use String for enum-like values (e.g., "BOTH")
    private String status;     // use String for enum-like values (e.g., "NOTIFIED_SUBSCRIBERS")

    // Nested objects
    private IngestResult ingestResult;
    private RetryPolicy retryPolicy;

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
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (schedule == null || schedule.isBlank()) return false;
        if (notifyOn == null || notifyOn.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Validate timestamps if present (must not be blank when provided)
        if (createdAt != null && createdAt.isBlank()) return false;
        if (startedAt != null && startedAt.isBlank()) return false;
        if (finishedAt != null && finishedAt.isBlank()) return false;

        // Validate ingestResult counts if provided
        if (ingestResult != null) {
            if (ingestResult.getCountAdded() != null && ingestResult.getCountAdded() < 0) return false;
            if (ingestResult.getCountUpdated() != null && ingestResult.getCountUpdated() < 0) return false;
            // errors list may be empty or null; no further constraints
        }

        // Validate retryPolicy if provided
        if (retryPolicy != null) {
            if (retryPolicy.getBackoffSeconds() != null && retryPolicy.getBackoffSeconds() < 0) return false;
            if (retryPolicy.getMaxRetries() != null && retryPolicy.getMaxRetries() < 0) return false;
        }

        return true;
    }

    @Data
    public static class IngestResult {
        private Integer countAdded;
        private Integer countUpdated;
        private List<String> errors;
    }

    @Data
    public static class RetryPolicy {
        private Integer backoffSeconds;
        private Integer maxRetries;
    }
}
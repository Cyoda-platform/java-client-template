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
    private String technicalId; // serialized technical identifier returned on POST
    private String id; // domain id
    private String status;
    private String scheduleDefinition;
    private String triggeredBy;
    private String startedAt; // ISO-8601 timestamp as String
    private String finishedAt; // ISO-8601 timestamp as String
    private String errorDetails;

    private IngestionSummary ingestionSummary;
    private NotificationPolicy notificationPolicy;
    private RetryPolicy retryPolicy;

    @Data
    public static class IngestionSummary {
        private Integer recordsFailed;
        private Integer recordsFetched;
        private Integer recordsProcessed;
    }

    @Data
    public static class NotificationPolicy {
        private String type;
    }

    @Data
    public static class RetryPolicy {
        private Integer backoffSeconds;
        private Integer maxAttempts;
    }

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
        // Required string fields: use isBlank() checks
        if (technicalId == null || technicalId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Validate notification policy if present
        if (notificationPolicy != null) {
            if (notificationPolicy.getType() == null || notificationPolicy.getType().isBlank()) return false;
        }

        // Validate retry policy if present
        if (retryPolicy != null) {
            if (retryPolicy.getBackoffSeconds() == null || retryPolicy.getBackoffSeconds() < 0) return false;
            if (retryPolicy.getMaxAttempts() == null || retryPolicy.getMaxAttempts() < 0) return false;
        }

        // Validate ingestion summary if present
        if (ingestionSummary != null) {
            if (ingestionSummary.getRecordsFetched() == null || ingestionSummary.getRecordsFetched() < 0) return false;
            if (ingestionSummary.getRecordsProcessed() == null || ingestionSummary.getRecordsProcessed() < 0) return false;
            if (ingestionSummary.getRecordsFailed() == null || ingestionSummary.getRecordsFailed() < 0) return false;
        }

        return true;
    }
}
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
    private String jobId; // technical id / external job identifier
    private String status;
    private String sourceUrl;
    private String errorInfo;
    private String scheduledAt; // ISO-8601 timestamp as String
    private String startedAt;   // ISO-8601 timestamp as String
    private String finishedAt;  // ISO-8601 timestamp as String
    private SourceSnapshot sourceSnapshot;
    private Summary summary;

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
        // Validate required String fields using isBlank()
        if (jobId == null || jobId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (scheduledAt == null || scheduledAt.isBlank()) return false;

        // Validate nested sourceSnapshot if present
        if (sourceSnapshot != null) {
            if (sourceSnapshot.getCursor() == null || sourceSnapshot.getCursor().isBlank()) return false;
            if (sourceSnapshot.getRecordCount() == null) return false;
            if (sourceSnapshot.getRecordCount() < 0) return false;
        }

        // Validate summary if present
        if (summary != null) {
            if (summary.getProcessed() == null) return false;
            if (summary.getProcessed() < 0) return false;
            if (summary.getNewCount() != null && summary.getNewCount() < 0) return false;
            if (summary.getInvalid() != null && summary.getInvalid() < 0) return false;
            if (summary.getUpdated() != null && summary.getUpdated() < 0) return false;
        }

        return true;
    }

    @Data
    public static class SourceSnapshot {
        private String cursor;
        private Integer recordCount;
        private String responseHash;
    }

    @Data
    public static class Summary {
        // Using different names to avoid Java keyword conflict and keep clarity
        private Integer invalid;
        private Integer newCount; // corresponds to "new" in example
        private Integer processed;
        private Integer updated;
    }
}
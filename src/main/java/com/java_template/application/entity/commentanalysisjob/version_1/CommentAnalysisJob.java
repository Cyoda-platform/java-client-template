package com.java_template.application.entity.commentanalysisjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class CommentAnalysisJob implements CyodaEntity {
    public static final String ENTITY_NAME = "CommentAnalysisJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    // Technical id returned by POST endpoints (serialized UUID)
    private String id;

    // Reference to the post being analyzed (serialized UUID)
    private String postId;

    // Email where the report will be sent
    private String recipientEmail;

    // When the analysis was requested (ISO-8601 string)
    private String requestedAt;

    // When the analysis was completed (ISO-8601 string) - optional
    private String completedAt;

    // Scheduling preference (e.g., "immediate", "scheduled")
    private String schedule;

    // Current job status (use String instead of enum)
    private String status;

    // Configuration for which metrics to compute
    private MetricsConfig metricsConfig;

    public CommentAnalysisJob() {}

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
        if (postId == null || postId.isBlank()) return false;
        if (recipientEmail == null || recipientEmail.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;
        if (schedule == null || schedule.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Validate metrics configuration
        if (metricsConfig == null) return false;
        List<String> metrics = metricsConfig.getMetrics();
        if (metrics == null || metrics.isEmpty()) return false;
        for (String m : metrics) {
            if (m == null || m.isBlank()) return false;
        }

        return true;
    }

    // Nested class to represent metrics configuration
    @Data
    public static class MetricsConfig {
        private List<String> metrics;
    }
}
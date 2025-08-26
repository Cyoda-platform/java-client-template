package com.java_template.application.entity.analysisreport.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class AnalysisReport implements CyodaEntity {
    public static final String ENTITY_NAME = "AnalysisReport"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String reportId; // report_id
    private String jobId; // job_id
    private Integer postId; // post_id
    private String recipientEmail; // recipient_email
    private String generatedAt; // generated_at (ISO timestamp)
    private String sentAt; // sent_at (ISO timestamp), may be null if not sent yet
    private String status; // status (e.g., SENT)
    private String summary; // summary
    private Metrics metrics;

    public AnalysisReport() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields (use isBlank for strings)
        if (reportId == null || reportId.isBlank()) return false;
        if (jobId == null || jobId.isBlank()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (recipientEmail == null || recipientEmail.isBlank()) return false;
        // Validate postId presence
        if (postId == null) return false;
        // Validate metrics presence and essential fields inside
        if (metrics == null) return false;
        if (metrics.getCount() == null) return false;
        // avgLengthWords may be null in some cases, topWords may be empty, sentimentSummary may be null
        return true;
    }

    @Data
    public static class Metrics {
        private Integer count; // number of comments analyzed
        private Integer avgLengthWords; // avg_length_words
        private String sentimentSummary; // sentiment_summary
        private List<String> topWords; // top_words
    }
}
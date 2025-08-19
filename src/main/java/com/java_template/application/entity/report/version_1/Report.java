package com.java_template.application.entity.report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

@Data
public class Report implements CyodaEntity {
    public static final String ENTITY_NAME = "Report";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String reportId; // report business id
    private String periodStart; // report period start (date string)
    private String periodEnd; // report period end (date string)
    private String generatedAt; // timestamp when generated (ISO string)
    private Map<String, Object> summaryMetrics = new HashMap<>(); // topSellers, lowMovers, restockCandidates, highlights
    private List<Map<String, Object>> attachments = new ArrayList<>(); // entries: {type, url, filename, size}
    private String status; // COMPILING, READY, SENT, FAILED
    private List<String> recipients = new ArrayList<>();
    private String createdFromJobId; // references ExtractionJob.jobId

    public Report() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (reportId == null || reportId.isBlank()) return false;
        if (periodStart == null || periodStart.isBlank()) return false;
        if (periodEnd == null || periodEnd.isBlank()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

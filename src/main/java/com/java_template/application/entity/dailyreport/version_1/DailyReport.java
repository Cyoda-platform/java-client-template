package com.java_template.application.entity.dailyreport.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DailyReport implements CyodaEntity {
    public static final String ENTITY_NAME = "DailyReport";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String reportDate; // ISO date for the report
    private String generatedAt; // ISO datetime
    private String jobTechnicalId; // IngestionJob technicalId used to build report
    private Map<String, Object> summaryMetrics; // totalActivities, perTypeCounts, perUserCounts, hourlyDistribution
    private List<Map<String, Object>> anomalies; // list of detected anomalies with context
    private List<String> recipients; // admin emails
    private String status; // CREATED/AGGREGATING/READY/PUBLISHED/FAILED
    private String publishedAt; // ISO datetime

    public DailyReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (this.reportDate == null || this.reportDate.isBlank()) return false;
        if (this.generatedAt == null || this.generatedAt.isBlank()) return false;
        if (this.jobTechnicalId == null || this.jobTechnicalId.isBlank()) return false;
        if (this.status == null || this.status.isBlank()) return false;
        return true;
    }
}

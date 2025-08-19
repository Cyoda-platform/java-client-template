package com.java_template.application.entity.report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Report implements CyodaEntity {
    public static final String ENTITY_NAME = "Report";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String reportId; // internal id
    private String jobRef; // ReportJob.jobName or job id
    private String periodFrom; // ISO date
    private String periodTo; // ISO date
    private Map<String, Object> metrics; // totalRevenue avgPrice bookingCount
    private List<Map<String, Object>> groupingBuckets; // group label -> metrics
    private String presentationType; // table chart
    private String generatedAt; // ISO datetime
    private String downloadUrl; // optional

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
        if (jobRef == null || jobRef.isBlank()) return false;
        if (periodFrom == null || periodFrom.isBlank()) return false;
        if (periodTo == null || periodTo.isBlank()) return false;
        if (metrics == null || metrics.isEmpty()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        return true;
    }
}

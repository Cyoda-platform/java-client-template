package com.java_template.application.entity.reportjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ReportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ReportJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String jobId; // technical id
    private String dataSourceUrl;
    private String generatedAt; // ISO-8601 timestamp as String
    private String notifyFilters;
    private String reportLocation;
    private String requestedMetrics;
    private String status;
    private String triggerType;

    public ReportJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields: ensure they are not null/blank
        if (jobId == null || jobId.isBlank()) return false;
        if (dataSourceUrl == null || dataSourceUrl.isBlank()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (triggerType == null || triggerType.isBlank()) return false;
        return true;
    }
}
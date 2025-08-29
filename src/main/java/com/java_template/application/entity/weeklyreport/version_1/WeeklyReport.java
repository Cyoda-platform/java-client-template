package com.java_template.application.entity.weeklyreport.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class WeeklyReport implements CyodaEntity {
    public static final String ENTITY_NAME = "WeeklyReport"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String reportId; // natural/business id, e.g., "weekly-summary-2025-W34"
    private String generatedAt; // ISO datetime string, e.g., "2025-08-25T09:15:00Z"
    private String weekStart; // ISO date string, e.g., "2025-08-18"
    private String status; // enum represented as String, e.g., "DISPATCHED"
    private String summary;
    private String attachmentUrl;

    public WeeklyReport() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: required string fields must be non-null and non-blank.
        if (reportId == null || reportId.isBlank()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        if (weekStart == null || weekStart.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // summary and attachmentUrl are optional but if provided should not be blank
        if (summary != null && summary.isBlank()) return false;
        if (attachmentUrl != null && attachmentUrl.isBlank()) return false;
        return true;
    }
}
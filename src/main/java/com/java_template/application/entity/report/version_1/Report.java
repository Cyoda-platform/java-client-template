package com.java_template.application.entity.report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Report Entity for Research & Clinical Trial Management platform
 * Represents analytics reports and metrics for tracking submission processes
 */
@Data
public class Report implements CyodaEntity {
    public static final String ENTITY_NAME = Report.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core business fields
    private String reportName;
    private String reportType; // SUBMISSION_STATUS, USER_ACTIVITY, PERFORMANCE_METRICS
    private String generatedBy; // Email of user who generated the report
    private LocalDateTime generationDate;
    private String parameters; // JSON string of report parameters
    private String dataRange; // Date range for report data (e.g., "2024-01-01,2024-12-31")
    private String format; // Report output format (PDF, CSV, JSON)
    private String filePath; // Storage path for generated report file

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return reportName != null && !reportName.trim().isEmpty() &&
               reportType != null && !reportType.trim().isEmpty() &&
               generatedBy != null && !generatedBy.trim().isEmpty() &&
               generationDate != null &&
               parameters != null && !parameters.trim().isEmpty() &&
               dataRange != null && !dataRange.trim().isEmpty() &&
               format != null && !format.trim().isEmpty() &&
               filePath != null && !filePath.trim().isEmpty() &&
               isValidReportType(reportType) &&
               isValidFormat(format) &&
               isValidEmail(generatedBy) &&
               isValidDataRange(dataRange);
    }

    /**
     * Validates if the report type is one of the allowed values
     */
    private boolean isValidReportType(String type) {
        return "SUBMISSION_STATUS".equals(type) || 
               "USER_ACTIVITY".equals(type) || 
               "PERFORMANCE_METRICS".equals(type);
    }

    /**
     * Validates if the format is one of the allowed values
     */
    private boolean isValidFormat(String format) {
        return "PDF".equals(format) || 
               "CSV".equals(format) || 
               "JSON".equals(format);
    }

    /**
     * Basic email validation
     */
    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }

    /**
     * Validates data range format (simplified - should be "YYYY-MM-DD,YYYY-MM-DD")
     */
    private boolean isValidDataRange(String dataRange) {
        if (dataRange == null || !dataRange.contains(",")) {
            return false;
        }
        
        String[] parts = dataRange.split(",");
        if (parts.length != 2) {
            return false;
        }
        
        // Basic date format validation (YYYY-MM-DD)
        return parts[0].matches("\\d{4}-\\d{2}-\\d{2}") && 
               parts[1].matches("\\d{4}-\\d{2}-\\d{2}");
    }
}

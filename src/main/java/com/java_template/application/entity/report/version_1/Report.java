package com.java_template.application.entity.report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Report implements CyodaEntity {
    public static final String ENTITY_NAME = Report.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Unique identifier
    private Long id;
    
    // Human-readable report name
    private String reportName;
    
    // Type of report (WEEKLY_SUMMARY, MONTHLY_ANALYSIS, CUSTOM)
    private String reportType;
    
    // When report was generated
    private LocalDateTime generationDate;
    
    // Start date of reporting period
    private LocalDate reportPeriodStart;
    
    // End date of reporting period
    private LocalDate reportPeriodEnd;
    
    // Path to generated report file
    private String filePath;
    
    // Report format (PDF, HTML, CSV)
    private String fileFormat;
    
    // Brief report summary for email body
    private String summary;
    
    // Number of products analyzed
    private Integer totalProducts;
    
    // List of best performing product names
    private List<String> topPerformingProducts;
    
    // List of products needing attention
    private List<String> underperformingProducts;
    
    // Main insights from analysis
    private List<String> keyInsights;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return reportName != null && !reportName.trim().isEmpty() &&
               reportType != null && !reportType.trim().isEmpty() &&
               reportPeriodStart != null && reportPeriodEnd != null &&
               (reportPeriodStart.isBefore(reportPeriodEnd) || reportPeriodStart.isEqual(reportPeriodEnd));
    }
}

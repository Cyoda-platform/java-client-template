package com.java_template.application.entity.report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class Report implements CyodaEntity {
    public static final String ENTITY_NAME = Report.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String reportId;
    
    // Report metadata
    private String reportType;
    private LocalDateTime generatedAt;
    private LocalDateTime reportPeriodStart;
    private LocalDateTime reportPeriodEnd;
    
    // Analytics data
    private Integer totalBooksAnalyzed;
    private Long totalPageCount;
    private Double averagePageCount;
    private String popularTitles; // JSON array
    private String publicationDateInsights; // JSON object
    private String reportSummary;
    
    // Email configuration
    private String emailRecipients;
    private LocalDateTime emailSentAt;
    
    // Relationships
    private UUID analyticsJobId;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation - more detailed validation is done in criteria
        return reportId != null && !reportId.trim().isEmpty() &&
               reportType != null && !reportType.trim().isEmpty() &&
               reportPeriodStart != null && reportPeriodEnd != null &&
               emailRecipients != null && emailRecipients.contains("@");
    }
}

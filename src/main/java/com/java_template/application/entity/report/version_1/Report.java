package com.java_template.application.entity.report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Report Entity
 * 
 * Represents an analysis report generated from CSV data. 
 * Contains analysis results and metadata about the report generation process.
 */
@Data
public class Report implements CyodaEntity {
    public static final String ENTITY_NAME = Report.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String reportId;
    
    // Required core business fields
    private String dataSourceId;
    private String title;
    
    // Optional fields for additional business data
    private String summary;
    private Map<String, Object> analysisResults;
    private LocalDateTime generatedAt;
    private String reportFormat;
    private Integer recipientCount;

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
        return reportId != null && 
               dataSourceId != null && 
               title != null && !title.trim().isEmpty();
    }
}

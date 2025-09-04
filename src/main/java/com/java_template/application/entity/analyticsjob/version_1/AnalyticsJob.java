package com.java_template.application.entity.analyticsjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

@Data
public class AnalyticsJob implements CyodaEntity {
    public static final String ENTITY_NAME = AnalyticsJob.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String jobId;
    
    // Job metadata
    private String jobType;
    private LocalDateTime scheduledFor;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    
    // Processing metrics
    private Integer booksProcessed;
    private Integer reportsGenerated;
    
    // Error handling
    private String errorMessage;
    
    // Job chaining
    private String nextJobId;
    
    // Configuration
    private String configurationData; // JSON configuration

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
        return jobType != null && !jobType.trim().isEmpty() &&
               scheduledFor != null &&
               booksProcessed != null && booksProcessed >= 0 &&
               reportsGenerated != null && reportsGenerated >= 0;
    }
}

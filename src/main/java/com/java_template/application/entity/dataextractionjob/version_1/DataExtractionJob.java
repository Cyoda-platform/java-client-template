package com.java_template.application.entity.dataextractionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

@Data
public class DataExtractionJob implements CyodaEntity {
    public static final String ENTITY_NAME = DataExtractionJob.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Unique identifier
    private Long id;
    
    // Name of extraction job
    private String jobName;
    
    // When job was scheduled to run
    private LocalDateTime scheduledTime;
    
    // When job actually started
    private LocalDateTime startTime;
    
    // When job completed
    private LocalDateTime endTime;
    
    // Type of data extracted (PRODUCTS, INVENTORY, ORDERS)
    private String extractionType;
    
    // Pet Store API endpoint used
    private String apiEndpoint;
    
    // Number of records successfully extracted
    private Integer recordsExtracted;
    
    // Number of records processed
    private Integer recordsProcessed;
    
    // Number of records that failed processing
    private Integer recordsFailed;
    
    // Details of any errors encountered
    private String errorLog;
    
    // When next job should run
    private LocalDateTime nextScheduledRun;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return jobName != null && !jobName.trim().isEmpty() &&
               extractionType != null && !extractionType.trim().isEmpty() &&
               scheduledTime != null;
    }
}

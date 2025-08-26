package com.java_template.application.entity.ingestionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class IngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String jobId; // technical id
    private String status;
    private String initiatedBy;
    private String sourceEndpoint;
    private String schedule;
    private String startedAt;
    private String finishedAt;
    private String errorSummary;
    private Integer processedCount;

    public IngestionJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank()
        if (jobId == null || jobId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (initiatedBy == null || initiatedBy.isBlank()) return false;
        if (sourceEndpoint == null || sourceEndpoint.isBlank()) return false;
        if (startedAt == null || startedAt.isBlank()) return false;
        // processedCount must be present and non-negative
        if (processedCount == null || processedCount < 0) return false;
        // If job is completed, finishedAt should be present
        if ("COMPLETED".equalsIgnoreCase(status) && (finishedAt == null || finishedAt.isBlank())) return false;
        return true;
    }
}
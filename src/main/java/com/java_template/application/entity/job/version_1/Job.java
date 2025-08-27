package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String jobId;
    private String state;
    private String sourceEndpoint;
    private String scheduledAt;
    private String startedAt;
    private String finishedAt;
    private Integer recordsFetchedCount;
    private String errorDetails;
    private Map<String, String> metadata;

    public Job() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields must be present and not blank
        if (jobId == null || jobId.isBlank()) return false;
        if (state == null || state.isBlank()) return false;
        if (sourceEndpoint == null || sourceEndpoint.isBlank()) return false;
        // scheduledAt is expected in examples; if present it must not be blank
        if (scheduledAt != null && scheduledAt.isBlank()) return false;
        // Numeric fields, if present, must be non-negative
        if (recordsFetchedCount != null && recordsFetchedCount < 0) return false;
        return true;
    }
}
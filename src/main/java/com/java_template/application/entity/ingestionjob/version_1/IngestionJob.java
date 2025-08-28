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
    // Technical id returned by POST endpoints (serialized UUID)
    private String id;

    private String jobName;
    private String mode;
    private Integer processedCount;
    private String sourceUrl;
    private String startedAt;
    private String completedAt;
    private String status;

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
        // Validate required string fields
        if (jobName == null || jobName.isBlank()) return false;
        if (mode == null || mode.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (startedAt == null || startedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // processedCount should be present (0 allowed) and non-negative
        if (processedCount == null || processedCount < 0) return false;
        return true;
    }
}
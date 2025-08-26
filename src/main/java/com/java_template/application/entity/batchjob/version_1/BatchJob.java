package com.java_template.application.entity.batchjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class BatchJob implements CyodaEntity {
    public static final String ENTITY_NAME = "BatchJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id (serialized UUID)
    private String id;
    private String jobName;
    private String runMonth; // e.g., "2025-09"
    private String scheduleCron;
    private String status; // use String for enums
    private String summary;
    // Timestamps as ISO-8601 strings
    private String createdAt;
    private String startedAt;
    private String finishedAt;

    public BatchJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required fields: jobName, runMonth, status, createdAt
        if (jobName == null || jobName.isBlank()) {
            return false;
        }
        if (runMonth == null || runMonth.isBlank()) {
            return false;
        }
        // Optional: basic validation for runMonth format YYYY-MM
        String runMonthPattern = "^\\d{4}-\\d{2}$";
        if (!runMonth.matches(runMonthPattern)) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        if (createdAt == null || createdAt.isBlank()) {
            return false;
        }
        // id is technical; may be absent for creation, so don't enforce here
        return true;
    }
}
package com.java_template.application.entity.importjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String jobName; // human name or description for this import
    private String requestedBy; // who asked to run the job
    private String payload; // single item or array of HN JSON payloads to import (stored as JSON string)
    private String createdAt; // ISO8601 UTC
    private String status; // PENDING/RUNNING/COMPLETED/FAILED
    private Integer processedCount; // how many items processed
    private Integer failureCount; // how many items failed

    public ImportJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // jobName, requestedBy and payload are required for creating an import job
        if (jobName == null || jobName.isBlank()) return false;
        if (requestedBy == null || requestedBy.isBlank()) return false;
        if (payload == null || payload.isBlank()) return false;
        return true;
    }
}

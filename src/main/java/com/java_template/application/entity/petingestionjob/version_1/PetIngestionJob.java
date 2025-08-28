package com.java_template.application.entity.petingestionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PetIngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "PetIngestionJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // ISO-8601 timestamps as strings
    private String startedAt;
    private String completedAt;
    // Job metadata
    private String jobName;
    private String sourceUrl;
    private String status; // use String for enum-like values (e.g., "COMPLETED", "FAILED", "RUNNING")
    // Processing details
    private Integer processedCount;
    private List<String> errors = new ArrayList<>();

    public PetIngestionJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields must be non-blank
        if (jobName == null || jobName.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (startedAt == null || startedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // processedCount must be present and non-negative
        if (processedCount == null || processedCount < 0) return false;
        // errors list must be present (can be empty)
        if (errors == null) return false;
        // If job is completed, completedAt must be provided
        if ("COMPLETED".equalsIgnoreCase(status) && (completedAt == null || completedAt.isBlank())) return false;
        return true;
    }
}
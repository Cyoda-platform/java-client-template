package com.java_template.application.entity.transformjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class TransformJob implements CyodaEntity {
    public static final String ENTITY_NAME = "TransformJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private String createdBy; // serialized UUID of the user who created the job
    private String jobType;
    private String status; // e.g., CREATED, RUNNING, COMPLETED, FAILED
    private String searchFilterId; // serialized UUID reference to SearchFilter
    private String outputLocation;
    private String errorMessage;
    private String startedAt; // ISO-8601 timestamp
    private String completedAt; // ISO-8601 timestamp
    private Integer priority;
    private Integer resultCount;
    private List<String> ruleNames;

    public TransformJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields must not be blank
        if (id == null || id.isBlank()) return false;
        if (createdBy == null || createdBy.isBlank()) return false;
        if (jobType == null || jobType.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (searchFilterId == null || searchFilterId.isBlank()) return false;

        // ruleNames must be present (can be empty)
        if (ruleNames == null) return false;

        // Numeric fields, if present, must be non-negative
        if (priority != null && priority < 0) return false;
        if (resultCount != null && resultCount < 0) return false;

        // If completed, outputLocation and completedAt must be present
        if ("COMPLETED".equalsIgnoreCase(status)) {
            if (outputLocation == null || outputLocation.isBlank()) return false;
            if (completedAt == null || completedAt.isBlank()) return false;
        }

        // If failed, errorMessage and completedAt must be present
        if ("FAILED".equalsIgnoreCase(status)) {
            if (errorMessage == null || errorMessage.isBlank()) return false;
            if (completedAt == null || completedAt.isBlank()) return false;
        }

        return true;
    }
}
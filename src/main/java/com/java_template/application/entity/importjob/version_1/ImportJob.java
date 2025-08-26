package com.java_template.application.entity.importjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id (serialized UUID) returned by POST endpoints
    private String id;

    private String sourceType;
    private String sourceLocation;
    private String fileFormat;
    private Map<String, Object> mappingRules;
    private String initiatedBy;
    private String status;
    private String errorDetails;

    private Integer recordsProcessed;
    private Integer recordsSucceeded;
    private Integer recordsFailed;

    private String createdAt;
    private String startedAt;
    private String completedAt;
    private String updatedAt;

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
        // id is a serialized UUID -> check not null (technical id)
        if (id == null) return false;

        // Use isBlank() checks for string fields
        if (sourceType == null || sourceType.isBlank()) return false;
        if (sourceLocation == null || sourceLocation.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (initiatedBy == null || initiatedBy.isBlank()) return false;

        // counts must be present and non-negative
        if (recordsProcessed == null || recordsProcessed < 0) return false;
        if (recordsSucceeded == null || recordsSucceeded < 0) return false;
        if (recordsFailed == null || recordsFailed < 0) return false;

        // mappingRules is expected (can be empty) but should not be null
        if (mappingRules == null) return false;

        return true;
    }
}
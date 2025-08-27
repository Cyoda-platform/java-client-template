package com.java_template.application.entity.getuserjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class GetUserJob implements CyodaEntity {
    public static final String ENTITY_NAME = "GetUserJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Timestamps as ISO-8601 strings
    private String createdAt;
    private String startedAt;
    private String completedAt;

    // Error message (nullable)
    private String errorMessage;

    // Foreign key reference to User (serialized id)
    private String requestUserId;

    // HTTP or external response code
    private Integer responseCode;

    // Job status as String (e.g., PENDING, STARTED, COMPLETED, FAILED)
    private String status;

    public GetUserJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic required fields
        if (createdAt == null || createdAt.isBlank()) return false;
        if (requestUserId == null || requestUserId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // If job has started, startedAt should be present
        if ("STARTED".equalsIgnoreCase(status)) {
            if (startedAt == null || startedAt.isBlank()) return false;
        }

        // If job is completed or failed, responseCode and completedAt should be present
        if ("COMPLETED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
            if (responseCode == null) return false;
            if (completedAt == null || completedAt.isBlank()) return false;
        }

        return true;
    }
}
package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // serialized UUID / technical id
    private Integer attempts;
    private String createdAt; // ISO timestamp as String
    private String updatedAt; // ISO timestamp as String
    private String scheduledAt; // ISO timestamp as String (optional)
    private String lastError;
    private Map<String, Object> payload; // flexible payload
    private List<String> petIds; // foreign key references as serialized UUIDs
    private List<String> subscriberIds; // foreign key references as serialized UUIDs
    private Map<String, Object> result; // flexible result object
    private String status;
    private String type;

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
        // Basic validation rules:
        // - id, type, status, createdAt and updatedAt must be non-blank Strings
        // - attempts must be non-null and >= 0
        // - payload must be non-null
        // - petIds and subscriberIds must be non-null lists and their entries non-blank
        if (id == null || id.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (updatedAt == null || updatedAt.isBlank()) return false;
        if (attempts == null || attempts < 0) return false;
        if (payload == null) return false;

        if (petIds == null || petIds.isEmpty()) return false;
        for (String pid : petIds) {
            if (pid == null || pid.isBlank()) return false;
        }

        if (subscriberIds == null) return false;
        for (String sid : subscriberIds) {
            if (sid == null || sid.isBlank()) return false;
        }

        // lastError and result are optional; scheduledAt is optional
        return true;
    }
}
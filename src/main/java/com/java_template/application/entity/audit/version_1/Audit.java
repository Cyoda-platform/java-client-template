package com.java_template.application.entity.audit.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Audit implements CyodaEntity {
    public static final String ENTITY_NAME = "Audit"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id (serialized UUID)
    private String auditId;
    // Action performed, e.g., "submit_for_review"
    private String action;
    // Actor who performed the action (serialized UUID)
    private String actorId;
    // Reference to the entity affected, e.g., "post-123:Post"
    private String entityRef;
    // Optional evidence reference (serialized UUID or token)
    private String evidenceRef;
    // Additional metadata about the audit event
    private Map<String, Object> metadata;
    // ISO-8601 timestamp string
    private String timestamp;

    public Audit() {} 

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
        if (auditId == null || auditId.isBlank()) return false;
        if (action == null || action.isBlank()) return false;
        if (actorId == null || actorId.isBlank()) return false;
        if (entityRef == null || entityRef.isBlank()) return false;
        if (timestamp == null || timestamp.isBlank()) return false;
        return true;
    }
}
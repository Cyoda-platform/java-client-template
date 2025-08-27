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
    private String audit_id;
    private String action;
    private String actor_id; // serialized UUID string reference to User
    private String entity_ref; // format like "post-123:Post"
    private String evidence_ref; // optional serialized UUID string
    private Map<String, Object> metadata;
    private String timestamp; // ISO-8601 string

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
        // Required fields: audit_id, action, actor_id, entity_ref, timestamp
        if (audit_id == null || audit_id.isBlank()) return false;
        if (action == null || action.isBlank()) return false;
        if (actor_id == null || actor_id.isBlank()) return false;
        if (entity_ref == null || entity_ref.isBlank()) return false;
        if (timestamp == null || timestamp.isBlank()) return false;
        return true;
    }
}
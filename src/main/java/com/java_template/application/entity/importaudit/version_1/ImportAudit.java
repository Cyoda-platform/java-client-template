package com.java_template.application.entity.importaudit.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class ImportAudit implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportAudit"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String auditId; // technical id for this audit record
    private Map<String, Object> details; // arbitrary JSON details about the audit
    private Long hnId; // reference to HN item id
    private String jobId; // reference to ImportJob id (serialized UUID / client id)
    private String outcome; // e.g., "SUCCESS", "FAILURE"
    private String timestamp; // ISO-8601 timestamp string

    public ImportAudit() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank (covers null and empty)
        if (auditId == null || auditId.isBlank()) return false;
        if (jobId == null || jobId.isBlank()) return false;
        if (outcome == null || outcome.isBlank()) return false;
        if (timestamp == null || timestamp.isBlank()) return false;
        // Validate numeric foreign key presence
        if (hnId == null) return false;
        // details may be optional but if present it should not be empty map
        // (allow null details)
        if (details != null && details.isEmpty()) return false;
        return true;
    }
}
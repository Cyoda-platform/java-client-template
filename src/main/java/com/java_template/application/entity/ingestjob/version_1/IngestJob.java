package com.java_template.application.entity.ingestjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class IngestJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    // Technical id for this IngestJob (returned by POST endpoints as a technical id)
    private String technicalId;

    // Optional caller id
    private String clientId;

    // ISO-8601 timestamp when the ingest job was created
    private String createdAt;

    // Optional error message if the ingest job failed
    private String errorMessage;

    // Payload representing the HN item; kept as a generic map to represent the JSON structure
    private Map<String, Object> hnPayload;

    // Status of the ingest job (e.g., PENDING, RUNNING, DONE, FAILED)
    private String status;

    // Reference to the stored item technical id (serialized UUID / storage identifier)
    private String storedItemTechnicalId;

    public IngestJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (hnPayload == null) return false;
        return true;
    }
}
package com.java_template.application.entity.petingestionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class PetIngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "PetIngestionJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    // Unique job identifier (technical id)
    private String jobId;
    // Who requested the import (serialized UUID as String)
    private String requestedBy;
    // Source URL or identifier for the ingestion
    private String source;
    // Job status (e.g., pending, running, completed, failed)
    private String status;
    // Timestamps in ISO-8601 format
    private String startedAt;
    private String completedAt;
    // Number of imported records
    private Integer importedCount;
    // Any errors encountered during the ingestion (serialized messages)
    private List<String> errors;

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
        // Validate required string fields
        if (jobId == null || jobId.isBlank()) return false;
        if (requestedBy == null || requestedBy.isBlank()) return false;
        if (source == null || source.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // startedAt should be present for a started/completed job
        if (startedAt == null || startedAt.isBlank()) return false;
        // importedCount must be present and non-negative
        if (importedCount == null || importedCount < 0) return false;
        // errors list must be non-null (can be empty)
        if (errors == null) return false;
        return true;
    }
}
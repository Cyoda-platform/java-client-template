package com.java_template.application.entity.importjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob"; 
    public static final Integer ENTITY_VERSION = 1;

    // Fields based on prototype
    private String jobId;         // technical id for the import job
    private String createdAt;     // ISO-8601 timestamp as String
    private Integer processedCount;
    private Integer failedCount;
    private String mode;          // e.g., "full", "incremental"
    private String status;        // e.g., "COMPLETED", "FAILED"
    private String notes;
    private String requestedBy;   // requester identifier (email or user id as String)
    private String sourceUrl;     // source URL used for the import

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
        // Validate required string fields
        if (jobId == null || jobId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (requestedBy == null || requestedBy.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Validate numeric fields
        if (processedCount == null || processedCount < 0) return false;
        if (failedCount == null || failedCount < 0) return false;
        if (failedCount > processedCount) return false;

        // mode and notes are optional; no further checks
        return true;
    }
}
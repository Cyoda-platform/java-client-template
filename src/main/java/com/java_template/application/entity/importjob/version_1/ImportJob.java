package com.java_template.application.entity.importjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String jobId; // technical id
    private String status;
    private String sourceUrl;
    private String startedAt; // ISO-8601 timestamp as String
    private String completedAt; // ISO-8601 timestamp as String, nullable
    private String initiatedBy;
    private Integer importedCount;
    private String errorSummary;

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
        if (status == null || status.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (startedAt == null || startedAt.isBlank()) return false;
        if (initiatedBy == null || initiatedBy.isBlank()) return false;

        // completedAt can be null, but if present should not be blank
        if (completedAt != null && completedAt.isBlank()) return false;

        // importedCount must be present and non-negative
        if (importedCount == null || importedCount < 0) return false;

        // errorSummary may be blank (empty string) to indicate no errors

        return true;
    }
}
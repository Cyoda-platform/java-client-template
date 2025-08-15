package com.java_template.application.entity.importjob.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String jobType; // e.g., single_item_import or batch_import
    private String payload; // JSON payload as string
    private String status; // PENDING, IN_PROGRESS, COMPLETED, FAILED
    private String errorMessage;
    private String createdAt; // ISO-8601 UTC
    private String startedAt; // ISO-8601 UTC
    private String finishedAt; // ISO-8601 UTC
    private String technicalId; // datastore-specific technical identifier

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
        // Validate mandatory fields for an ImportJob request: jobType and payload must be present
        if (this.jobType == null || this.jobType.isBlank()) return false;
        if (this.payload == null || this.payload.isBlank()) return false;
        return true;
    }
}

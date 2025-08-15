package com.java_template.application.entity.importtask.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ImportTask implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportTask";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String jobTechnicalId; // reference to ImportJob.technicalId
    private Integer attemptNumber; // 1-based attempt counter
    private String attemptedAt; // ISO-8601 UTC timestamp for the attempt
    private String result; // JSON result details as string
    private String status; // PENDING, IN_PROGRESS, SUCCEEDED, FAILED
    private String technicalId; // datastore-specific technical identifier

    public ImportTask() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields: jobTechnicalId must be a non-blank string; attemptNumber must be >= 1
        if (this.jobTechnicalId == null || this.jobTechnicalId.isBlank()) return false;
        if (this.attemptNumber == null || this.attemptNumber < 1) return false;
        return true;
    }
}

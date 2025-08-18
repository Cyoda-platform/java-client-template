package com.java_template.application.entity.importjob.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Objects;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String jobId; // business id
    private String sourceUrl; // Petstore API endpoint
    private String requestedBy; // user id (serialized UUID)
    private String status; // PENDING/RUNNING/COMPLETED/FAILED
    private Integer importedCount;
    private String errorMessage;

    // Additional fields expected by processors
    private Boolean notificationSent;
    private String updatedAt;

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
        if (jobId == null || jobId.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (requestedBy == null || requestedBy.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (importedCount == null || importedCount < 0) return false;
        return true;
    }

    // Compatibility helpers used by processors
    public String getTechnicalId() {
        return this.jobId;
    }

    public void setTechnicalId(String technicalId) {
        this.jobId = technicalId;
    }
}

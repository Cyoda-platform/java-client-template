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

    private String jobId; // business id
    private String initiatedBy; // user/admin
    private String sourceUrl; // Petstore endpoint
    private String startedAt; // datetime as String
    private String completedAt; // datetime as String
    private String status; // PENDING IN_PROGRESS COMPLETED FAILED
    private Integer importedCount;
    private String errorMessage;

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
        if (initiatedBy == null || initiatedBy.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        return true;
    }
}

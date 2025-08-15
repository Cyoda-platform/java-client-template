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
    private String jobId; // technical id for the import job
    private String itemId; // reference to HackerNewsItem.id (serialized UUID or string)
    private String status; // e.g., "PENDING", "COMPLETED", "FAILED"
    private Long createdAt; // epoch millis

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
        return jobId != null && !jobId.isBlank()
                && itemId != null && !itemId.isBlank()
                && status != null && !status.isBlank();
    }
}

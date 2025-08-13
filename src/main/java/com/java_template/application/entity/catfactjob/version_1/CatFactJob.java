package com.java_template.application.entity.catfactjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class CatFactJob implements CyodaEntity {
    public static final String ENTITY_NAME = "CatFactJob";
    public static final Integer ENTITY_VERSION = 1;

    private String scheduledAt; // ISO8601 datetime string
    private String status; // job status: PENDING, COMPLETED, FAILED

    public CatFactJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (scheduledAt == null || scheduledAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

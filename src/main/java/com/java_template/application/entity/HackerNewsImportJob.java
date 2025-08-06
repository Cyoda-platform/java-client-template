package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class HackerNewsImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "HackerNewsImportJob";

    private String technicalId;
    private String importTimestamp; // ISO-8601 format
    private String status; // PENDING, COMPLETED, FAILED
    private Integer itemCount;
    private String description;

    public HackerNewsImportJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (status == null || status.isBlank()) {
            return false;
        }
        if (itemCount == null || itemCount < 0) {
            return false;
        }
        return true;
    }
}

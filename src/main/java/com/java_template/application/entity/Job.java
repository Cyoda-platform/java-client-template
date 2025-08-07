package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.OffsetDateTime;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";

    private Long id;
    private String externalId;
    private String state;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
    private String resultSummary;

    public Job() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return externalId != null && !externalId.isBlank()
            && state != null && !state.isBlank();
    }
}

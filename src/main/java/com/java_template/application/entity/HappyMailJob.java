package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class HappyMailJob implements CyodaEntity {
    public static final String ENTITY_NAME = "HappyMailJob";

    private String mailTechnicalId;
    private String status;
    private LocalDateTime createdAt;

    public HappyMailJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (mailTechnicalId == null || mailTechnicalId.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        if (createdAt == null) {
            return false;
        }
        return true;
    }
}}

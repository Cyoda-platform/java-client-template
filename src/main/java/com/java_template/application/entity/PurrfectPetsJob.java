package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PurrfectPetsJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database technical ID

    private String jobId;
    private String petType;
    private String action;
    private StatusEnum status;
    private LocalDateTime createdAt;

    public PurrfectPetsJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("purrfectPetsJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "purrfectPetsJob");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (jobId == null || jobId.isBlank()) return false;
        if (petType == null || petType.isBlank()) return false;
        if (action == null || action.isBlank()) return false;
        if (status == null) return false;
        if (createdAt == null) return false;
        return true;
    }

    public enum StatusEnum {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}

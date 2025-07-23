package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;
import java.time.LocalDateTime;

@Data
public class PetJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private LocalDateTime createdAt;
    private String petType;
    private String operation;
    private String status; // e.g., PENDING, PROCESSING, COMPLETED, FAILED

    public PetJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petJob");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (createdAt == null) return false;
        if (petType == null || petType.isBlank()) return false;
        if (operation == null || operation.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

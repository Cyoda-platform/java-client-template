package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PurrfectPetJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database technical ID

    private String petType; // e.g., cat, dog, bird
    private String action; // operation requested, e.g., ADD, SEARCH
    private String payload; // JSON data as String
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

    public PurrfectPetJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("purrfectPetJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "purrfectPetJob");
    }

    @Override
    public boolean isValid() {
        return !(petType == null || petType.isBlank() ||
                 action == null || action.isBlank() ||
                 status == null || status.isBlank());
    }
}

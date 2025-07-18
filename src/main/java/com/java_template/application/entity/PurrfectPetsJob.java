package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;

@Data
public class PurrfectPetsJob implements CyodaEntity {
    private String id; // business ID - jobId
    private UUID technicalId; // database ID
    private String operationType; // type of operation e.g., "ImportPets", "SyncFavorites"
    private String payload; // JSON stored as String
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

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
        return id != null && !id.isBlank() &&
               operationType != null && !operationType.isBlank() &&
               status != null && !status.isBlank();
    }
}

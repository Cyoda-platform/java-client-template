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
    private UUID technicalId; // database ID

    private String jobId; // unique identifier for the orchestration job
    private String actionType; // type of action e.g., FETCH_PETS, UPDATE_PET_STATUS
    private LocalDateTime createdAt; // timestamp of job creation
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
        return jobId != null && !jobId.isBlank() &&
                actionType != null && !actionType.isBlank() &&
                createdAt != null &&
                status != null && !status.isBlank();
    }
}

package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PurrfectPetsJob implements CyodaEntity {
    private String jobName; // descriptive name of the pet loading/saving job
    private String requestedAction; // e.g., "LOAD_PETS", "SAVE_PET"
    private String status; // entity lifecycle state (PENDING, PROCESSING, COMPLETED, FAILED)

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
        if (jobName == null || jobName.isBlank()) return false;
        if (requestedAction == null || requestedAction.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

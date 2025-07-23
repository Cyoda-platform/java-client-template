package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;

@Data
public class PetCreationJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String jobId; // unique identifier for the job
    private String petData; // raw data payload for pet creation as JSON string
    private String status; // JobStatusEnum values: PENDING, PROCESSING, COMPLETED, FAILED

    public PetCreationJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petCreationJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petCreationJob");
    }

    @Override
    public boolean isValid() {
        return (id != null && !id.isBlank())
            && (jobId != null && !jobId.isBlank())
            && (petData != null && !petData.isBlank())
            && (status != null && !status.isBlank());
    }
}

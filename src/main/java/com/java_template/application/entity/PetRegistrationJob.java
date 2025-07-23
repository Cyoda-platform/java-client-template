package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;

@Data
public class PetRegistrationJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String jobId;
    private String petName;
    private String petType;
    private String ownerName;
    private String status; // Could be enum, using String for simplicity

    public PetRegistrationJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petRegistrationJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petRegistrationJob");
    }

    @Override
    public boolean isValid() {
        return jobId != null && !jobId.isBlank() &&
               petName != null && !petName.isBlank() &&
               petType != null && !petType.isBlank() &&
               ownerName != null && !ownerName.isBlank() &&
               status != null && !status.isBlank();
    }
}

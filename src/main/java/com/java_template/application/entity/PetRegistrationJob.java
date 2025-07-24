package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PetRegistrationJob implements CyodaEntity {
    private String petName;
    private String petType;
    private String petStatus;
    private String ownerName;
    private LocalDateTime createdAt;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

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
        return petName != null && !petName.isBlank()
            && petType != null && !petType.isBlank()
            && petStatus != null && !petStatus.isBlank()
            && ownerName != null && !ownerName.isBlank()
            && createdAt != null
            && status != null && !status.isBlank();
    }
}

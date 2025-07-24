package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PetStatusUpdate implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String petId; // reference to the Pet entity (UUID serialized as String)
    private String newStatus; // new status for the pet, e.g., AVAILABLE, SOLD
    private LocalDateTime updatedAt; // timestamp of status update
    private String status; // PENDING, PROCESSED

    public PetStatusUpdate() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petStatusUpdate");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petStatusUpdate");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() && petId != null && !petId.isBlank() && newStatus != null && !newStatus.isBlank() && status != null && !status.isBlank();
    }
}

package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;
import java.time.LocalDateTime;

@Data
public class AdoptionRequest implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String petId; // foreign key to Pet entity
    private String requesterName;
    private LocalDateTime requestDate;
    private String status; // e.g., SUBMITTED, APPROVED, REJECTED

    public AdoptionRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("adoptionRequest");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "adoptionRequest");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (requesterName == null || requesterName.isBlank()) return false;
        if (requestDate == null) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

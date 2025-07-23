package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PetEvent implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database technical ID

    private String petId; // reference to Pet entity as String
    private String eventType; // CREATED, UPDATED, ADOPTED
    private LocalDateTime timestamp;
    private String status; // RECORDED, PROCESSED

    public PetEvent() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petEvent");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petEvent");
    }

    @Override
    public boolean isValid() {
        return !(petId == null || petId.isBlank() ||
                 eventType == null || eventType.isBlank() ||
                 status == null || status.isBlank());
    }
}

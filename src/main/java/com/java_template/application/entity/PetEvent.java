package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PetEvent implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String eventId;
    private String petId;
    private String eventType;
    private LocalDateTime eventTimestamp;
    private String payload;
    private String status;

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
        return (eventId != null && !eventId.isBlank()) &&
               (petId != null && !petId.isBlank()) &&
               (eventType != null && !eventType.isBlank()) &&
               (eventTimestamp != null) &&
               (status != null && !status.isBlank());
    }
}

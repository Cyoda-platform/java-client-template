package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.Map;
import java.util.UUID;

@Data
public class PetUpdateEvent implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String eventId;
    private String petId; // foreign key as String (UUID serialized)
    private Map<String, Object> updatedFields;
    private String status; // EventStatusEnum (PENDING, PROCESSED, FAILED) stored as String

    public PetUpdateEvent() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petUpdateEvent");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petUpdateEvent");
    }

    @Override
    public boolean isValid() {
        return eventId != null && !eventId.isBlank()
            && petId != null && !petId.isBlank()
            && status != null && !status.isBlank();
    }
}

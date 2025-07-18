package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;

@Data
public class AdoptionRequest implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String petId; // foreign key to Pet, serialized UUID
    private String userId;
    private String status; // pending|approved|rejected

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
        return id != null && !id.isBlank() && technicalId != null
                && petId != null && !petId.isBlank()
                && userId != null && !userId.isBlank()
                && status != null && !status.isBlank();
    }
}

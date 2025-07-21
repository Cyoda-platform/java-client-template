package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;

@Data
public class Favorite implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String favoriteId;
    private String userId;
    private String petId;
    private String status; // e.g., ACTIVE, REMOVED

    public Favorite() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("favorite");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "favorite");
    }

    @Override
    public boolean isValid() {
        return favoriteId != null && !favoriteId.isBlank()
                && userId != null && !userId.isBlank()
                && petId != null && !petId.isBlank()
                && status != null && !status.isBlank();
    }
}

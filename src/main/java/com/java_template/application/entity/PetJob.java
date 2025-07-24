package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;

@Data
public class PetJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private Long petId; // foreign key reference to Pet
    private String operation; // e.g., CREATE, PROCESS, SEARCH
    private String requestPayload; // JSON string
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

    public PetJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petJob");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
                && petId != null
                && operation != null && !operation.isBlank()
                && status != null && !status.isBlank();
    }
}

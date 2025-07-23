package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.UUID;
import java.time.LocalDateTime;

@Data
public class PetJob implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String petId; // UUID as String reference
    private String jobType;
    private LocalDateTime createdAt;
    private String status;

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
            && technicalId != null
            && petId != null && !petId.isBlank()
            && jobType != null && !jobType.isBlank()
            && createdAt != null
            && status != null && !status.isBlank();
    }
}

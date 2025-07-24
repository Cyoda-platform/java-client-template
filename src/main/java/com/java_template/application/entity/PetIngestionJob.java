package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.UUID;
import java.time.LocalDateTime;

@Data
public class PetIngestionJob implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String sourceUrl;
    private LocalDateTime createdAt;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

    public PetIngestionJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petIngestionJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petIngestionJob");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() && technicalId != null && sourceUrl != null && !sourceUrl.isBlank() && createdAt != null && status != null && !status.isBlank();
    }
}

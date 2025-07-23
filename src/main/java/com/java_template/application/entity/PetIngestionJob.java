package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PetIngestionJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private LocalDateTime createdAt; // job creation timestamp
    private String sourceUrl; // URL of Petstore API or data source
    private JobStatusEnum status; // job lifecycle state

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
        return id != null && !id.isBlank() && sourceUrl != null && !sourceUrl.isBlank() && status != null;
    }
}

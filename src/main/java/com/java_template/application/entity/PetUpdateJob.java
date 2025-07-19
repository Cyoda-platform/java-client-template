package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PetUpdateJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String jobId;
    private LocalDateTime requestedAt;
    private String source;
    private String status;

    public PetUpdateJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petUpdateJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petUpdateJob");
    }

    @Override
    public boolean isValid() {
        return (jobId != null && !jobId.isBlank()) &&
               (source != null && !source.isBlank()) &&
               (status != null && !status.isBlank());
    }
}

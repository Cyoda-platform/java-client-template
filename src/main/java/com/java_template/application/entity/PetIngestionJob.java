package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PetIngestionJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String jobId; // unique identifier for the ingestion job
    private String source; // data source reference, e.g., Petstore API
    private LocalDateTime createdAt; // timestamp of job creation
    private String status; // JobStatusEnum (PENDING, PROCESSING, COMPLETED, FAILED)

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
        return jobId != null && !jobId.isBlank() &&
               source != null && !source.isBlank() &&
               createdAt != null &&
               status != null && !status.isBlank();
    }
}

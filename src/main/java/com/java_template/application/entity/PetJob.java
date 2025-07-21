package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PetJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String requestType; // e.g., FETCH_ALL, FETCH_BY_TYPE
    private String petType; // optional filter like "cat", "dog"
    private StatusEnum status; // PENDING, PROCESSING, COMPLETED, FAILED
    private LocalDateTime createdAt; // job creation timestamp
    private Integer resultCount; // number of pets retrieved

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
                && requestType != null && !requestType.isBlank()
                && status != null;
    }

    public enum StatusEnum {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}

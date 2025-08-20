package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.Instant;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;

    // Orchestration fields
    private String jobId; // serialized UUID
    private String type; // e.g., ORDER_PLACE, CART_SYNC
    private String status; // e.g., PENDING, RUNNING, COMPLETED, FAILED
    private String requestId; // idempotency/request correlation
    private String targetEntityId; // technical id of the target domain entity
    private String payload; // JSON payload for the job

    private Instant createdAt;
    private Instant updatedAt;

    public Job() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (jobId == null || jobId.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        return true;
    }
}

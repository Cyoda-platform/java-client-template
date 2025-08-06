package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.Instant;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";

    private String jobType; // "INGESTION" or "NOTIFICATION"
    private String status; // "PENDING", "RUNNING", "COMPLETED", "FAILED"
    private String scheduledAt; // ISO 8601 timestamp
    private String createdAt; // ISO 8601 timestamp
    private String details; // Optional details

    public Job() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (jobType == null || jobType.isBlank()) return false;
        if (!(jobType.equals("INGESTION") || jobType.equals("NOTIFICATION"))) return false;
        if (scheduledAt == null || scheduledAt.isBlank()) return false;
        // createdAt may be null on creation
        return true;
    }
}}

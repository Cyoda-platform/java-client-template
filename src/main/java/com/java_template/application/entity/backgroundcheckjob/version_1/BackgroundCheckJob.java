package com.java_template.application.entity.backgroundcheckjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class BackgroundCheckJob implements CyodaEntity {
    public static final String ENTITY_NAME = "BackgroundCheckJob"; 
    public static final Integer ENTITY_VERSION = 1;

    // Technical id (serialized UUID)
    private String id;

    // Reference to the applicant/user (serialized UUID)
    private String applicantId;

    // Current status of the background check job (e.g., "pending", "in_progress", "completed", "failed")
    private String status;

    // External service job identifier (if any)
    private String externalJobId;

    // ISO-8601 timestamp when job was submitted
    private String submittedAt;

    // ISO-8601 timestamp when job was completed (nullable until completion)
    private String completedAt;

    // URL or location of the generated background report (nullable until available)
    private String reportUrl;

    // Optional free-form notes about the job
    private String notes;

    public BackgroundCheckJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required fields: id, applicantId, status, submittedAt
        if (id == null || id.isBlank()) return false;
        if (applicantId == null || applicantId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (submittedAt == null || submittedAt.isBlank()) return false;
        // completedAt, reportUrl, externalJobId, notes are optional
        return true;
    }
}
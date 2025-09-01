package com.java_template.application.entity.weeklysendjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class WeeklySendJob implements CyodaEntity {
    public static final String ENTITY_NAME = "WeeklySendJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String catFactTechnicalId; // serialized UUID reference to CatFact
    private String createdAt; // ISO-8601 timestamp when job was created
    private String runAt; // ISO-8601 timestamp when job actually ran
    private String scheduledFor; // ISO-8601 timestamp when job was scheduled
    private String status; // job status (use String for enum-like values)
    private String errorMessage; // optional error message if job failed

    public WeeklySendJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields must be non-null and non-blank
        if (catFactTechnicalId == null || catFactTechnicalId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (runAt == null || runAt.isBlank()) return false;
        if (scheduledFor == null || scheduledFor.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // errorMessage is optional (can be null or blank)
        return true;
    }
}
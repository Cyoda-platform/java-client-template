package com.java_template.application.entity.batchjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BatchJob implements CyodaEntity {
    public static final String ENTITY_NAME = "BatchJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private List<String> adminEmails;
    private String apiEndpoint;
    private String createdAt;
    private String jobName;
    private String lastRunTimestamp;
    private Map<String, Object> metadata;
    private String scheduleCron;
    private String status;

    public BatchJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields
        if (jobName == null || jobName.isBlank()) return false;
        if (apiEndpoint == null || apiEndpoint.isBlank()) return false;
        if (scheduleCron == null || scheduleCron.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // Validate admin emails list if present
        if (adminEmails == null || adminEmails.isEmpty()) return false;
        for (String email : adminEmails) {
            if (email == null || email.isBlank()) return false;
        }

        // metadata and lastRunTimestamp are optional, no strict checks
        return true;
    }
}
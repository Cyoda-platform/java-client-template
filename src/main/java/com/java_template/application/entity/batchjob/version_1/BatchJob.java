package com.java_template.application.entity.batchjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class BatchJob implements CyodaEntity {
    public static final String ENTITY_NAME = "BatchJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String jobName; // friendly name for the run
    private String scheduledFor; // ISO datetime when job should run
    private String timezone; // job timezone
    private List<String> adminEmails; // recipients for reports
    private String status; // PENDING, IN_PROGRESS, COMPLETED, FAILED
    private String createdAt; // ISO datetime
    private String startedAt; // ISO datetime
    private String finishedAt; // ISO datetime
    private Integer processedUserCount; // summary metric
    private String errorMessage; // last error

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
        if (jobName == null || jobName.isBlank()) return false;
        if (scheduledFor == null || scheduledFor.isBlank()) return false;
        if (timezone == null || timezone.isBlank()) return false;
        if (adminEmails == null || adminEmails.isEmpty()) return false;
        return true;
    }
}
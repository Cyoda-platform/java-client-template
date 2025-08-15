package com.java_template.application.entity.job.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String technicalId;
    private String name;
    private String sourceUrl;
    private String schedule;
    private String triggerType;
    private Integer maxRecords;
    private String status;
    private String scheduledAt;
    private String startedAt;
    private String finishedAt;
    private Integer processedCount;
    private Integer successCount;
    private Integer failureCount;
    private String resultSummary;
    private String errorDetails;
    private List<String> subscriberIds;
    private Object rawResponse;
    private String createdAt;
    private String updatedAt;

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
        if (name == null || name.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (schedule == null || schedule.isBlank()) return false;
        if (triggerType == null || triggerType.isBlank()) return false;
        if (maxRecords == null || maxRecords <= 0) return false;
        return true;
    }
}

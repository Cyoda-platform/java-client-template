package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private String schedule;
    private String scope;
    private String source;
    private String startedAt;
    private String finishedAt;
    private String status;
    private Boolean notificationsSent;
    private List<String> subscribersSnapshot = new ArrayList<>();
    private List<String> errorDetails = new ArrayList<>();
    private ResultSummary resultSummary;

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
        // Required string fields must be present and not blank
        if (id == null || id.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (source == null || source.isBlank()) return false;
        if (startedAt == null || startedAt.isBlank()) return false;
        // resultSummary must be present and valid
        if (resultSummary == null || !resultSummary.isValid()) return false;
        // notificationsSent can be null (unknown) but if present must be non-null (Boolean covers that)
        return true;
    }

    @Data
    public static class ResultSummary {
        private Integer errorCount;
        private Integer ingestedCount;
        private Integer updatedCount;

        public boolean isValid() {
            if (errorCount == null || ingestedCount == null || updatedCount == null) return false;
            if (errorCount < 0 || ingestedCount < 0 || updatedCount < 0) return false;
            return true;
        }
    }
}
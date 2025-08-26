package com.java_template.application.entity.importjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob"; 
    public static final Integer ENTITY_VERSION = 1;

    // Fields based on prototype
    private String jobId;
    private String jobType; // use String for enum-like values
    private String status;
    private String sourceReference;
    private String createdAt; // ISO-8601 timestamp as String

    private ResultSummary resultSummary;

    public ImportJob() {} 

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
        if (jobType == null || jobType.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (sourceReference == null || sourceReference.isBlank()) return false;
        if (resultSummary == null) return false;
        if (resultSummary.getCreated() == null || resultSummary.getCreated() < 0) return false;
        if (resultSummary.getFailed() == null || resultSummary.getFailed() < 0) return false;
        if (resultSummary.getUpdated() == null || resultSummary.getUpdated() < 0) return false;
        return true;
    }

    @Data
    public static class ResultSummary {
        private Integer created;
        private Integer failed;
        private Integer updated;
    }
}
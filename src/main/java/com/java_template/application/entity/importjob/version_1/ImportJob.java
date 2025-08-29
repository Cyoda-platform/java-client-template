package com.java_template.application.entity.importjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String jobId;
    private String createdAt;
    private String filterSpecies;
    private String requestedBy;
    private String sourceUrl;
    private String status;
    private Summary summary;

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
        // Validate required string fields
        if (jobId == null || jobId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (requestedBy == null || requestedBy.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // summary must be present and counts must be non-negative
        if (summary == null) return false;
        if (summary.getImported() == null || summary.getImported() < 0) return false;
        if (summary.getFailed() == null || summary.getFailed() < 0) return false;

        // filterSpecies is optional (may be null or blank)
        return true;
    }

    @Data
    public static class Summary {
        private Integer failed;
        private Integer imported;
    }
}
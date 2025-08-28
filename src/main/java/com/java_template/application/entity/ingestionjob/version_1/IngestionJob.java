package com.java_template.application.entity.ingestionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class IngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String requestedBy; // serialized UUID or user id
    private String sourceUrl;
    private String startedAt; // ISO-8601 timestamp
    private String completedAt; // ISO-8601 timestamp, optional
    private String status; // use String for enum-like values (e.g., "COMPLETED")
    private Summary summary; // aggregated results

    public IngestionJob() {} 

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
        if (requestedBy == null || requestedBy.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (startedAt == null || startedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // If completedAt is present it must not be blank
        if (completedAt != null && completedAt.isBlank()) return false;

        // If summary is present, ensure counts are non-null and non-negative
        if (summary != null) {
            if (summary.getCreated() == null || summary.getFailed() == null || summary.getUpdated() == null) {
                return false;
            }
            if (summary.getCreated() < 0 || summary.getFailed() < 0 || summary.getUpdated() < 0) {
                return false;
            }
        }

        return true;
    }

    @Data
    public static class Summary {
        private Integer created;
        private Integer failed;
        private Integer updated;

        public Summary() {}
    }
}
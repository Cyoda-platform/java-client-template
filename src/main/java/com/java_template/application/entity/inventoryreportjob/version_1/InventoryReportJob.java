package com.java_template.application.entity.inventoryreportjob.version_1;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class InventoryReportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "InventoryReportJob";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String technicalId;
    private String jobName;
    private String requestedBy;
    private List<String> metricsRequested;
    private JsonNode filters; // changed to JsonNode to match processors/criteria expectations
    private List<String> groupBy;
    private String presentationType;
    private Map<String, Object> schedule;
    private OffsetDateTime createdAt;
    private String status; // PENDING/IN_PROGRESS/COMPLETED/FAILED
    private String reportRef; // added so processors can reference intermediate report
    private Map<String, Object> metadata; // optional metadata to support legacy formats
    private OffsetDateTime retentionUntil; // optional retention

    public InventoryReportJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required orchestration fields
        if (jobName == null || jobName.isBlank()) return false;
        if (requestedBy == null || requestedBy.isBlank()) return false;
        if (metricsRequested == null || metricsRequested.isEmpty()) return false;
        if (presentationType == null || presentationType.isBlank()) return false;
        return true;
    }
}

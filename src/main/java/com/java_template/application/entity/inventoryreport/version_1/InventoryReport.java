package com.java_template.application.entity.inventoryreport.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class InventoryReport implements CyodaEntity {
    public static final String ENTITY_NAME = "InventoryReport";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String technicalId;
    private String reportName;
    private String jobRef; // reference to InventoryReportJob technicalId
    private OffsetDateTime generatedAt;
    private String status; // SUCCESS/FAILED/EMPTY
    private Map<String, Object> metricsSummary;
    private List<Map<String, Object>> groupedSummaries;
    private Map<String, Object> presentationPayload;
    private String errorMessage;
    private OffsetDateTime retentionUntil;
    private String suggestion; // added so processors can set suggestions

    public InventoryReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (reportName == null || reportName.isBlank()) return false;
        if (jobRef == null || jobRef.isBlank()) return false;
        if (generatedAt == null) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

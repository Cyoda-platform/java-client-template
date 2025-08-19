package com.java_template.application.entity.inventoryreportjob.version_1;

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
    private Map<String, String> filters; // e.g., category, location, minDate/maxDate, supplier
    private List<String> groupBy;
    private String presentationType; // table, chart
    private String schedule; // optional; cron or interval description serialized as String
    private OffsetDateTime createdAt;
    private String status; // PENDING/IN_PROGRESS/COMPLETED/FAILED

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
        if (jobName == null || jobName.isBlank()) return false;
        if (requestedBy == null || requestedBy.isBlank()) return false;
        if (metricsRequested == null || metricsRequested.isEmpty()) return false;
        return true;
    }
}

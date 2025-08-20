package com.java_template.application.entity.report.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.Map;
import java.util.List;

@Data
public class Report implements CyodaEntity {
    public static final String ENTITY_NAME = "Report";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String reportDate; // ISO date the report summarizes
    private String generatedAt; // timestamp
    private String summary; // textual summary
    private Map<String, Object> metrics; // aggregated KPIs e.g., totals, per-type counts
    private List<Map<String, Object>> anomalies; // list of flagged anomalies
    private List<String> recipients; // list of admin emails
    private String deliveryStatus; // PENDING/SENT/FAILED
    private String archivedAt; // timestamp

    public Report() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (reportDate == null || reportDate.isBlank()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        return true;
    }
}

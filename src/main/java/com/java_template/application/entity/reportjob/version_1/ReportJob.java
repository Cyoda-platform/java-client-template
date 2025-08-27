package com.java_template.application.entity.reportjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ReportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ReportJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String createdAt; // ISO-8601 timestamp string
    private List<String> exportFormats;
    private Map<String, String> filters;
    private String notify; // email or notification target
    private String requestedBy; // user identifier (serialized UUID or email)
    private String status; // use String for enum-like values (e.g., "COMPLETED")
    private String title;
    private String visualization;

    public ReportJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required fields: title, requestedBy, createdAt, exportFormats (non-empty)
        if (title == null || title.isBlank()) {
            return false;
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            return false;
        }
        if (createdAt == null || createdAt.isBlank()) {
            return false;
        }
        if (exportFormats == null || exportFormats.isEmpty()) {
            return false;
        }
        // If notify is provided it must not be blank
        if (notify != null && notify.isBlank()) {
            return false;
        }
        // status and visualization are optional but if present should not be blank
        if (status != null && status.isBlank()) {
            return false;
        }
        if (visualization != null && visualization.isBlank()) {
            return false;
        }
        return true;
    }
}
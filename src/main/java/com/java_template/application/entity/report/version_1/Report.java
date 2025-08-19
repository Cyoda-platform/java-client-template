package com.java_template.application.entity.report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Report implements CyodaEntity {
    public static final String ENTITY_NAME = "Report";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String technicalId;
    private String reportId; // business id
    private Integer postId;
    private String generatedAt;
    private String summary;
    private Map<String, Object> metrics; // counts, sentimentTotals, topKeywords
    private List<Map<String, Object>> highlights; // sample comments / alerts
    private List<String> recipients;
    private String deliveryStatus; // PENDING / SENT / FAILED

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
        if (reportId == null || reportId.isBlank()) return false;
        if (postId == null || postId <= 0) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        return true;
    }
}

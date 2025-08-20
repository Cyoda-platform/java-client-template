package com.java_template.application.entity.report.version_1; // replace {entityName} with actual entity name in lowercase

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

    private String technicalId; // internal UUID
    private String reportId; // report business id
    private String date; // reporting date
    private String generatedAt; // timestamp
    private List<Map<String, Object>> summaryItems; // array of objects containing pattern_type, metrics, confidence
    private String recipientEmail; // recipient email
    private String deliveryStatus; // PENDING/SENT/FAILED/READY
    private Integer deliveryAttempts; // attempt counter
    private Map<String, Object> lastDeliveryResponse; // provider response details

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
        if (date == null || date.isBlank()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        if (recipientEmail == null || recipientEmail.isBlank()) return false;
        if (deliveryStatus == null || deliveryStatus.isBlank()) return false;
        return true;
    }
}
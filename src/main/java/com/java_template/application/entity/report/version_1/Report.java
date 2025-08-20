package com.java_template.application.entity.report.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Report implements CyodaEntity {
    public static final String ENTITY_NAME = "Report";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String report_id; // report business id
    private String date; // reporting date
    private String generated_at; // timestamp
    private String summary_items; // serialized JSON array of objects containing pattern_type, metrics, confidence
    private String recipient_email; // recipient email
    private String delivery_status; // PENDING/SENT/FAILED

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
        if (report_id == null || report_id.isBlank()) return false;
        if (date == null || date.isBlank()) return false;
        if (generated_at == null || generated_at.isBlank()) return false;
        if (recipient_email == null || recipient_email.isBlank()) return false;
        if (delivery_status == null || delivery_status.isBlank()) return false;
        return true;
    }
}
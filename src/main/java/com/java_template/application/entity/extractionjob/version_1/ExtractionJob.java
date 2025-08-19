package com.java_template.application.entity.extractionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

@Data
public class ExtractionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ExtractionJob";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String jobId; // business id for the job
    private String schedule; // human/cron-like representation
    private String sourceUrl; // Pet Store API endpoint base
    private Map<String, Object> parameters = new HashMap<>(); // filters, formats to request
    private List<String> recipients = new ArrayList<>(); // email addresses to send report to
    private String reportTemplateId; // reference to chosen report layout
    private String lastRunAt; // timestamp of last attempt (ISO string)
    private String status; // PENDING, SCHEDULED, RUNNING, FAILED, COMPLETED, CANCELLED
    private String createdAt; // timestamp when created (ISO string)

    public ExtractionJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (jobId == null || jobId.isBlank()) return false;
        if (schedule == null || schedule.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (recipients == null || recipients.isEmpty()) return false;
        if (reportTemplateId == null || reportTemplateId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

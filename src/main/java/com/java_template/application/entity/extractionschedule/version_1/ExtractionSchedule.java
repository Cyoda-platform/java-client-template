package com.java_template.application.entity.extractionschedule.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class ExtractionSchedule implements CyodaEntity {
    public static final String ENTITY_NAME = "ExtractionSchedule";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String schedule_id; // human identifier
    private String frequency; // e.g., weekly
    private String day; // e.g., Monday
    private String time; // HH:MM
    private String timezone; // IANA timezone
    private String last_run; // timestamp
    private String status; // SCHEDULED | RUNNING | PAUSED | FAILED
    private List<String> recipients; // list of emails
    private String format; // PDF or CSV

    public ExtractionSchedule() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (schedule_id == null || schedule_id.isBlank()) return false;
        if (frequency == null || frequency.isBlank()) return false;
        if (day == null || day.isBlank()) return false;
        if (time == null || time.isBlank()) return false;
        if (timezone == null || timezone.isBlank()) return false;
        if (format == null || format.isBlank()) return false;
        if (recipients == null || recipients.isEmpty()) return false;
        return true;
    }
}
package com.java_template.application.entity.fetchjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class FetchJob implements CyodaEntity {
    public static final String ENTITY_NAME = "FetchJob";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String name; // human name for the job
    private String runDay; // e.g., Wednesday
    private String runTime; // HH:mm
    private String timezone; // timezone to interpret runTime
    private String recurrence; // weekly
    private List<String> recipients; // email list for reports
    private OffsetDateTime lastRunAt;
    private OffsetDateTime nextRunAt;
    private String status; // scheduled/paused/running/failed
    private String triggeredBy; // manual/schedule
    private Map<String, Object> parameters; // e.g., topNPopular

    public FetchJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (name == null || name.isBlank()) return false;
        if (runDay == null || runDay.isBlank()) return false;
        if (runTime == null || runTime.isBlank()) return false;
        if (timezone == null || timezone.isBlank()) return false;
        if (recurrence == null || recurrence.isBlank()) return false;
        if (recipients == null || recipients.isEmpty()) return false;
        return true;
    }
}

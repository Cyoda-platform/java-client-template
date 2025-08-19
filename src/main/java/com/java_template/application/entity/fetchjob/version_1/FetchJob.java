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

    private String technicalId; // serialized id used by API responses
    private String name;
    private String runDay;
    private String runTime;
    private String timezone;
    private String recurrence;
    private List<String> recipients;
    private OffsetDateTime lastRunAt;
    private OffsetDateTime nextRunAt;
    private String status;
    private String triggeredBy;
    private Map<String, Object> parameters;

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

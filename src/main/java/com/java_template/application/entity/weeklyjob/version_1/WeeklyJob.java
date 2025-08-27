package com.java_template.application.entity.weeklyjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class WeeklyJob implements CyodaEntity {
    public static final String ENTITY_NAME = "WeeklyJob";
    public static final Integer ENTITY_VERSION = 1;

    // Technical id (serialized UUID)
    private String id;

    // Core fields from requirements
    private String name;
    private String apiEndpoint;
    private String failurePolicy;
    private String lastRunAt;   // ISO timestamp as String
    private String nextRunAt;   // ISO timestamp as String
    private List<String> recipients;
    private String recurrenceDay;
    private String runTime;     // e.g., "09:00"
    private String status;
    private String timezone;

    public WeeklyJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // id is optional for creation; if present it must not be blank
        if (id != null && id.isBlank()) return false;

        if (name == null || name.isBlank()) return false;
        if (recurrenceDay == null || recurrenceDay.isBlank()) return false;
        if (runTime == null || runTime.isBlank()) return false;
        if (timezone == null || timezone.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // recipients must be present and each entry must be non-blank
        if (recipients == null || recipients.isEmpty()) return false;
        for (String r : recipients) {
            if (r == null || r.isBlank()) return false;
        }

        // Optional string fields must not be blank if provided
        if (apiEndpoint != null && apiEndpoint.isBlank()) return false;
        if (failurePolicy != null && failurePolicy.isBlank()) return false;
        if (lastRunAt != null && lastRunAt.isBlank()) return false;
        if (nextRunAt != null && nextRunAt.isBlank()) return false;

        return true;
    }
}
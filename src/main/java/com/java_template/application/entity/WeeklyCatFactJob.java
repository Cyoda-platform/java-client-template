package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class WeeklyCatFactJob implements CyodaEntity {
    public static final String ENTITY_NAME = "WeeklyCatFactJob";

    private String subscriberEmail; // email of the subscriber signing up
    private String catFact;         // the cat fact retrieved from the API for the week
    private String status;          // job processing status: PENDING, COMPLETED, FAILED
    private String scheduledAt;     // timestamp when the job is scheduled to run

    public WeeklyCatFactJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (subscriberEmail == null || subscriberEmail.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (scheduledAt == null || scheduledAt.isBlank()) return false;
        return true;
    }
}

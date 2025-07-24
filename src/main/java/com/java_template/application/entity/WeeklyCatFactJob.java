package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class WeeklyCatFactJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private OffsetDateTime scheduledAt; // when the job is scheduled to run

    private String status; // job lifecycle state: PENDING, PROCESSING, COMPLETED, FAILED

    public WeeklyCatFactJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("weeklyCatFactJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "weeklyCatFactJob");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (scheduledAt == null) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        return true;
    }
}

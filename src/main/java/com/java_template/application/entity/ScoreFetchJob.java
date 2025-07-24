package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.Instant;
import java.time.LocalDate;

@Data
public class ScoreFetchJob implements CyodaEntity {
    private LocalDate jobDate; // date for which NBA scores are fetched
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    private Instant triggeredAt; // timestamp when job started
    private Instant completedAt; // timestamp when job finished

    public ScoreFetchJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("scoreFetchJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "scoreFetchJob");
    }

    @Override
    public boolean isValid() {
        return jobDate != null && status != null && !status.isBlank();
    }
}

package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class NbaScoresFetchJob implements CyodaEntity {
    private LocalDate scheduledDate;
    private LocalTime fetchTimeUTC;
    private String status; // PENDING, IN_PROGRESS, COMPLETED, FAILED
    private String summary;

    public NbaScoresFetchJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("nbaScoresFetchJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "nbaScoresFetchJob");
    }

    @Override
    public boolean isValid() {
        return scheduledDate != null && fetchTimeUTC != null && status != null && !status.isBlank();
    }
}

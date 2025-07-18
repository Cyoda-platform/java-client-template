package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.UUID;

@Data
public class GameScoreFetchJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private LocalDate scheduledDate; // date for which NBA scores are fetched
    private String status; // JobStatusEnum as String (PENDING, RUNNING, COMPLETED, FAILED)
    private Timestamp createdAt; // job creation time
    private Timestamp updatedAt; // last update time

    public GameScoreFetchJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("gameScoreFetchJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "gameScoreFetchJob");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() && scheduledDate != null && status != null && !status.isBlank();
    }
}

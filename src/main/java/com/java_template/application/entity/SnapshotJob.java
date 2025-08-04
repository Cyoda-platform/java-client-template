package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class SnapshotJob implements CyodaEntity {
    public static final String ENTITY_NAME = "SnapshotJob";

    private String season; // The Bundesliga season year, e.g., "2023"
    private String dateRangeStart; // ISO date, start of snapshot capture period
    private String dateRangeEnd; // ISO date, end of snapshot capture period
    private String status; // Job status: PENDING, COMPLETED, FAILED
    private String createdAt; // Timestamp when job was created

    public SnapshotJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (season == null || season.isBlank()) return false;
        if (dateRangeStart == null || dateRangeStart.isBlank()) return false;
        if (dateRangeEnd == null || dateRangeEnd.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DailyScoresJob implements CyodaEntity {
    private String date; // The date for which NBA scores are fetched, format YYYY-MM-DD
    private String status; // Current job status: PENDING, PROCESSING, COMPLETED, FAILED
    private String createdAt; // Timestamp when the job was created
    private String completedAt; // Timestamp when the job finished processing

    public DailyScoresJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("dailyScoresJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "dailyScoresJob");
    }

    @Override
    public boolean isValid() {
        if (date == null || date.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        // completedAt can be null if job not finished yet
        return true;
    }
}

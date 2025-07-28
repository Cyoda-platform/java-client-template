package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class HackerNewsItemJob implements CyodaEntity {
    private final String className = this.getClass().getSimpleName();

    private String technicalId; // Datastore-generated unique identifier, not part of entity JSON
    private String hnItemJson; // Raw JSON string of the Hacker News item in Firebase HN API format
    private String status; // Processing status: PENDING, PROCESSING, COMPLETED, FAILED
    private Long createdAt; // Epoch timestamp of job creation
    private Long completedAt; // Epoch timestamp of job completion, optional

    public HackerNewsItemJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(className);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, className);
    }

    @Override
    public boolean isValid() {
        if (hnItemJson == null || hnItemJson.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        return true;
    }
}

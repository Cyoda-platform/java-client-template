package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DigestRequestJob implements CyodaEntity {
    private final String className = this.getClass().getSimpleName();

    private String userEmail; // email address to send the digest to
    private String eventMetadata; // JSON or stringified metadata about the digest request
    private String status; // job status: PENDING, PROCESSING, COMPLETED, FAILED
    private String createdAt; // timestamp of job creation
    private String completedAt; // timestamp of job completion

    public DigestRequestJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(className);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, className);
    }

    @Override
    public boolean isValid() {
        if (userEmail == null || userEmail.isBlank()) return false;
        if (eventMetadata == null || eventMetadata.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // createdAt and completedAt can be null initially
        return true;
    }
}

package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.Instant;

@Data
public class DigestRequest implements CyodaEntity {
    private final String className = this.getClass().getSimpleName();

    private String userEmail;
    private String requestMetadata;
    private String externalApiEndpoint;
    private Instant requestTimestamp;
    private String status;

    public DigestRequest() {}

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
        if (externalApiEndpoint == null || externalApiEndpoint.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

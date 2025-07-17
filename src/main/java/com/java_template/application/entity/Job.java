package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.Instant;

@Data
public class Job implements CyodaEntity {
    private String status;
    private Instant timestamp;
    private String email;
    private String metadataJson;

    public Job() {}

    public Job(String status, Instant timestamp, String email, String metadataJson) {
        this.status = status;
        this.timestamp = timestamp;
        this.email = email;
        this.metadataJson = metadataJson;
    }

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("job");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "job");
    }

    @Override
    public boolean isValid() {
        return status != null && !status.isEmpty()
                && timestamp != null
                && email != null && !email.isEmpty()
                && metadataJson != null && !metadataJson.isEmpty();
    }
}

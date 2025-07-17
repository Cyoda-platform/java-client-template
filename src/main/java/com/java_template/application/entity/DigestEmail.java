package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.Instant;

@Data
public class DigestEmail implements CyodaEntity {
    private String id;
    private String digestRequestId;
    private String content; // HTML or plain text
    private String status;
    private Instant sentAt;

    public DigestEmail() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("digestEmail");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "digestEmail");
    }

    @Override
    public boolean isValid() {
        return digestRequestId != null && !digestRequestId.isEmpty() && content != null && !content.isEmpty();
    }
}

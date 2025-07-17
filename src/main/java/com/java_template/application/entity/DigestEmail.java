package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.Instant;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DigestEmail implements CyodaEntity {
    private String id;
    private String digestRequestId;
    private String emailContent;
    private String sentStatus;
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
        return digestRequestId != null && !digestRequestId.isEmpty() && emailContent != null && !emailContent.isEmpty();
    }
}

package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.Instant;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DigestEmail implements CyodaEntity {
    private String jobTechnicalId;
    private String emailContent;
    private Instant sentAt;
    private String deliveryStatus;

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
        if (jobTechnicalId == null || jobTechnicalId.isBlank()) {
            return false;
        }
        if (emailContent == null || emailContent.isBlank()) {
            return false;
        }
        if (sentAt == null) {
            return false;
        }
        if (deliveryStatus == null || deliveryStatus.isBlank()) {
            return false;
        }
        return true;
    }
}

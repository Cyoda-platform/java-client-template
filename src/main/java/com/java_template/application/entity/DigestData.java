package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;

@Data
public class DigestData implements CyodaEntity {
    private String jobTechnicalId;
    private String retrievedData;
    private String format;
    private LocalDateTime createdAt;

    public DigestData() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("digestData");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "digestData");
    }

    @Override
    public boolean isValid() {
        return jobTechnicalId != null && !jobTechnicalId.isBlank() && retrievedData != null && !retrievedData.isBlank() && format != null && !format.isBlank();
    }
}

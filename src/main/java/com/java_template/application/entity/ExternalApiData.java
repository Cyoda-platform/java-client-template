package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.Instant;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class ExternalApiData implements CyodaEntity {
    private String jobTechnicalId;
    private String apiEndpoint;
    private String responseData;
    private Instant fetchedAt;

    public ExternalApiData() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("externalApiData");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "externalApiData");
    }

    @Override
    public boolean isValid() {
        if (jobTechnicalId == null || jobTechnicalId.isBlank()) {
            return false;
        }
        if (apiEndpoint == null || apiEndpoint.isBlank()) {
            return false;
        }
        if (responseData == null || responseData.isBlank()) {
            return false;
        }
        if (fetchedAt == null) {
            return false;
        }
        return true;
    }
}

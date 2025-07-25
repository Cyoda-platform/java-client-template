package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DigestData implements CyodaEntity {
    private String digestRequestId;
    private String retrievedData;
    private String formatType;

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
        if (digestRequestId == null || digestRequestId.isBlank()) {
            return false;
        }
        if (retrievedData == null || retrievedData.isBlank()) {
            return false;
        }
        if (formatType == null || formatType.isBlank()) {
            return false;
        }
        return true;
    }
}

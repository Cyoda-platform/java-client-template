package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class LEIEnrichmentRequest implements CyodaEntity {
    // Entity fields
    private String businessId;
    private String leiSource;
    private String status;
    private String lei;

    public LEIEnrichmentRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("leiEnrichmentRequest");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "leiEnrichmentRequest");
    }

    @Override
    public boolean isValid() {
        if (businessId == null || businessId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (lei == null) return false;
        // leiSource can be blank or null as optional
        return true;
    }
}

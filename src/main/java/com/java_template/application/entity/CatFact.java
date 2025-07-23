package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.UUID;

@Data
public class CatFact implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String fact;
    private String source;
    private String status;

    public CatFact() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("catFact");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "catFact");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
            && technicalId != null
            && fact != null && !fact.isBlank()
            && source != null && !source.isBlank()
            && status != null && !status.isBlank();
    }
}

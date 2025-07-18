package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.UUID;

@Data
public class Job implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String status;
    private String description;

    public Job() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("job");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "job");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() && technicalId != null && status != null && !status.isBlank();
    }
}

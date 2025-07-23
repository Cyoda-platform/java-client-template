package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CatFactJob implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private LocalDateTime scheduledAt;
    private String catFactText;
    private String status; // Could be enum, use String for simplicity

    public CatFactJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("catFactJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "catFactJob");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
            && scheduledAt != null
            && status != null && !status.isBlank();
    }
}

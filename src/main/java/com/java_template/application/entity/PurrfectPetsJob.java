package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PurrfectPetsJob implements CyodaEntity {
    private String technicalId;
    private String requestedStatus;
    private LocalDateTime processedAt;
    private String status;

    public PurrfectPetsJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("purrfectPetsJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "purrfectPetsJob");
    }

    @Override
    public boolean isValid() {
        if (requestedStatus == null || requestedStatus.isBlank()) return false;
        if (!(requestedStatus.equals("available") || requestedStatus.equals("pending") || requestedStatus.equals("sold"))) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

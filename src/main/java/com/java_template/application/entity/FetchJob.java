package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class FetchJob implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private LocalDate scheduledDate;
    private StatusEnum status;
    private String resultSummary;

    public FetchJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("fetchJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "fetchJob");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (scheduledDate == null) return false;
        if (status == null) return false;
        return true;
    }

    public enum StatusEnum {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}

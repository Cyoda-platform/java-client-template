package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class Notification implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String subscriberId;
    private String jobId;
    private StatusEnum status;
    private OffsetDateTime sentAt;

    public Notification() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("notification");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "notification");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (subscriberId == null || subscriberId.isBlank()) return false;
        if (jobId == null || jobId.isBlank()) return false;
        if (status == null) return false;
        return true;
    }

    public enum StatusEnum {
        PENDING,
        SENT,
        FAILED
    }
}

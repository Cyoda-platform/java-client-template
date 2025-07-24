package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class Pet implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database technical ID

    private String petId;
    private String name;
    private String category;
    private String status;
    private List<String> photoUrls;
    private StatusEnum lifecycleStatus;
    private LocalDateTime createdAt;

    public Pet() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("pet");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "pet");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (lifecycleStatus == null) return false;
        if (createdAt == null) return false;
        return true;
    }

    public enum StatusEnum {
        NEW,
        PROCESSED,
        ARCHIVED
    }
}

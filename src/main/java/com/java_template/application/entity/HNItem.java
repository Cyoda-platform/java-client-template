package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class HNItem implements CyodaEntity {

    private String technicalId; // UUID as String
    private String id; // Original HN item ID
    private String payload; // JSON string containing full HN item
    private String status; // INVALID, VALIDATED
    private String createdAt; // ISO8601 timestamp string

    public HNItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("hnItem");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "hnItem");
    }

    @Override
    public boolean isValid() {
        if (technicalId == null || technicalId.isBlank()) return false;
        if (id == null || id.isBlank()) return false;
        if (payload == null || payload.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        return true;
    }
}

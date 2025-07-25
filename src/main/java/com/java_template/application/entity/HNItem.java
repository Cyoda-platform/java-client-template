package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

/**
 * ABOUTME: HNItem entity representing Hacker News items with business data only.
 * Entity metadata (technicalId, createdAt) is managed by Cyoda platform and retrieved via entityService.getItemWithMetaFields.
 */
@Data
public class HNItem implements CyodaEntity {

    private String id; // Original HN item ID
    private String payload; // JSON string containing full HN item
    private String status; // INVALID, VALIDATED

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
        if (id == null || id.isBlank()) return false;
        if (payload == null || payload.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

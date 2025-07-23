package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class HNItem implements CyodaEntity {
    private String id; // business ID
    private String type; // Hacker News item type
    private String content; // JSON string of full HN item payload
    private String status; // StatusEnum: INVALID, VALIDATED
    private UUID technicalId; // database ID

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
        // Validate presence of mandatory fields id and type
        if (id == null || id.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        return true;
    }
}

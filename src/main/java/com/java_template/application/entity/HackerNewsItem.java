package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class HackerNewsItem implements CyodaEntity {

    private String rawJson;
    private String id;
    private String type;
    private String state;
    private String createdAt;

    public HackerNewsItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("hackerNewsItem");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "hackerNewsItem");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (type == null || type.isBlank()) {
            return false;
        }
        return true;
    }
}

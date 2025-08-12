package com.java_template.application.entity.hackernewsitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.Instant;

@Data
public class HackerNewsItem implements CyodaEntity {
    public static final String ENTITY_NAME = "HackerNewsItem";
    public static final Integer ENTITY_VERSION = 1;

    private Long id; // mandatory
    private String type; // mandatory
    private String json; // optional
    private String state; // mandatory, one of "START", "INVALID", "VALID"
    private String invalidReason; // optional
    private Instant creationTimestamp; // mandatory

    public HackerNewsItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (id == null) {
            return false;
        }
        if (type == null || type.isBlank()) {
            return false;
        }
        if (state == null || state.isBlank()) {
            return false;
        }
        if (creationTimestamp == null) {
            return false;
        }
        return true;
    }
}

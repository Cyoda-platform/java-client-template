package com.java_template.application.entity.hnitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class HNItem implements CyodaEntity {
    public static final String ENTITY_NAME = "HNItem";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String by;
    private Integer descendants;
    private Long id;
    private List<Long> kids;
    private String rawJson; // serialized JSON string of the raw payload
    private Integer score;
    private String text;
    private Long time;
    private String title;
    private String type;
    private String url;

    public HNItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields:
        // - id must be present
        // - 'by' must be non-blank
        // - time must be present
        // - title should be non-blank for story items
        if (id == null) {
            return false;
        }
        if (by == null || by.isBlank()) {
            return false;
        }
        if (time == null) {
            return false;
        }
        if (title == null || title.isBlank()) {
            return false;
        }
        // other fields are optional
        return true;
    }
}
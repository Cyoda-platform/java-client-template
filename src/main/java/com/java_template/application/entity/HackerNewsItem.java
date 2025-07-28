package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class HackerNewsItem implements CyodaEntity {
    private final String className = this.getClass().getSimpleName();

    private Long id; // Hacker News item unique identifier from JSON
    private String by; // Author username
    private Integer descendants; // Number of comments
    private List<Long> kids; // IDs of child comments
    private Integer score; // Score of the item
    private Long time; // Unix timestamp of submission
    private String title; // Title of the story/comment
    private String type; // Item type: story, comment, etc.
    private String url; // URL of the story
    private String rawJson; // Full original JSON string stored as-is

    public HackerNewsItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(className);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, className);
    }

    @Override
    public boolean isValid() {
        if (by == null || by.isBlank()) {
            return false;
        }
        if (title == null || title.isBlank()) {
            return false;
        }
        if (type == null || type.isBlank()) {
            return false;
        }
        if (rawJson == null || rawJson.isBlank()) {
            return false;
        }
        if (id == null || id <= 0) {
            return false;
        }
        return true;
    }
}

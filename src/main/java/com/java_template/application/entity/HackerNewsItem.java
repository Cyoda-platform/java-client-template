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

    private Long hnId; // Hacker News official item ID, unique per item
    private String by; // author username
    private String type; // type of item, e.g., story, comment, poll
    private Long time; // UNIX timestamp when item was created
    private String title; // story title, if applicable
    private String url; // story URL, if applicable
    private Integer score; // points or score of the item
    private String text; // comment or text content, if applicable
    private List<Long> kids; // IDs of child items, e.g., comments
    private Long parent; // ID of the parent item, if applicable
    private Integer descendants; // total comment count, if applicable

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
        if (hnId == null) return false;
        if (by == null || by.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        if (time == null) return false;
        return true;
    }
}

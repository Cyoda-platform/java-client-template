package com.java_template.application.entity.task.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Task implements CyodaEntity {
    public static final String ENTITY_NAME = "Task";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business identifier for the task
    private String projectId; // reference to the parent project.id
    private String title; // task title
    private String description; // task description
    private String status; // current lifecycle status of the task (e.g., pending, assigned, in_progress, in_review, completed, cancelled)
    private String assigneeId; // reference id to the person assigned; may be null
    private String dueDate; // ISO 8601 due date; may be null
    private String priority; // priority label, e.g., low/medium/high
    private List<String> dependencies; // list of task ids that must complete before this task starts
    private String createdAt; // ISO 8601 datetime when the entity was created
    private String updatedAt; // ISO 8601 datetime when the entity was last updated
    private Map<String, Object> metadata; // freeform key/value map for custom attributes

    public Task() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation rules based on the functional requirements
        if (id == null || id.isBlank()) return false;
        if (projectId == null || projectId.isBlank()) return false;
        if (title == null || title.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // dependencies can be null or empty; metadata can be null
        return true;
    }
}

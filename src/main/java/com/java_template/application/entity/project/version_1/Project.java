package com.java_template.application.entity.project.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Project implements CyodaEntity {
    public static final String ENTITY_NAME = "Project";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business identifier for the project
    private String name; // project title
    private String description; // project description and goals
    private String startDate; // ISO 8601 start date
    private String endDate; // ISO 8601 end date
    private String status; // current lifecycle status of the project (e.g., created, planning, active, completed, archived)
    private String ownerId; // reference id for the project owner
    private String createdAt; // ISO 8601 datetime when the entity was created
    private String updatedAt; // ISO 8601 datetime when the entity was last updated
    private Map<String, Object> metadata; // freeform key/value map for custom attributes

    public Project() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (startDate == null || startDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

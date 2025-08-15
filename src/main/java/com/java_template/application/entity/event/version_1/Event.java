package com.java_template.application.entity.event.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Event implements CyodaEntity {
    public static final String ENTITY_NAME = "Event";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String eventId;
    private String title;
    private String description;
    private String date;
    private String location;
    private String category;
    private Integer capacity;
    private String createdAt;
    private String updatedAt;
    private String status;

    public Event() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (eventId == null || eventId.isBlank()) return false;
        if (title == null || title.isBlank()) return false;
        if (date == null || date.isBlank()) return false;
        if (capacity == null || capacity < 0) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}

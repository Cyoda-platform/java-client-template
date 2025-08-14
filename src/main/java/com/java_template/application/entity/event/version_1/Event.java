package com.java_template.application.entity.event.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Event implements CyodaEntity {
    public static final String ENTITY_NAME = "Event";
    public static final Integer ENTITY_VERSION = 1;

    private String eventName;
    private String eventType;
    private String eventDate;
    private String location;
    private String des cription;
    private Integer capacity;

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
        return !(eventName == null || eventName.isBlank()) 
            && !(eventType == null || eventType.isBlank()) 
            && !(eventDate == null || eventDate.isBlank()) 
            && !(location == null || location.isBlank()) 
            && capacity != null && capacity > 0;
    }
}
